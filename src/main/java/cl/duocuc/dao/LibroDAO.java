package cl.duocuc.dao;

import cl.duocuc.conexion.DatabaseConnection;
import cl.duocuc.exception.PersistenciaException;
import cl.duocuc.model.Libro;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code libros}. Implementa {@link CrudDAO} con el CRUD
 * completo del catálogo.
 * <p>
 * Todas las lecturas hacen un {@code LEFT JOIN} con {@code categorias} para traer
 * también el nombre de la categoría. Así la interfaz muestra "Ciencia Ficción" en
 * la tabla en vez del número de la clave foránea, y sigue siendo este DAO el único
 * que conoce el SQL.
 * <p>
 * <b>Control de consistencia del stock.</b> La columna {@code stock} la escriben dos
 * caminos distintos: el CRUD de este DAO y la transacción de préstamos del
 * {@code PrestamoDAO}. Si el CRUD guardara siempre el número que muestra el
 * formulario, los préstamos ocurridos mientras el bibliotecario tenía la ficha abierta
 * se perderían, porque ese número quedó congelado al abrirla. Para que no pase, la
 * actualización está separada en dos:
 * <ul>
 *   <li>{@link #actualizar(Libro)} guarda la ficha <b>sin tocar el stock</b>. Es lo que
 *       se usa cuando el bibliotecario cambió el título, el autor, el ISBN, la
 *       editorial o la categoría, y por lo tanto no puede chocar con un préstamo.</li>
 *   <li>{@link #actualizarConStock(Libro, int)} se usa solo cuando el stock cambió, y
 *       lo escribe con un <b>bloqueo optimista</b>: el {@code UPDATE} lleva
 *       {@code AND stock = ?} con el valor que el formulario había leído, así que si
 *       entretanto hubo un préstamo o una devolución no actualiza ninguna fila y el
 *       conflicto se avisa en vez de pisar el dato.</li>
 * </ul>
 */
public class LibroDAO implements CrudDAO<Libro> {

    /** Columnas comunes a todas las lecturas, con el nombre de la categoría incluido. */
    private static final String SQL_BASE =
            "SELECT l.id, l.titulo, l.autor, l.isbn, l.editorial, l.stock, "
                    + "l.id_categoria, c.nombre AS categoria "
                    + "FROM libros l LEFT JOIN categorias c ON c.id = l.id_categoria ";

    private static final String SQL_SELECT = SQL_BASE + "ORDER BY l.titulo";

    private static final String SQL_SELECT_BY_ID = SQL_BASE + "WHERE l.id = ?";

    /** Búsqueda por título, autor o ISBN, usada por el buscador del módulo. */
    private static final String SQL_BUSCAR = SQL_BASE
            + "WHERE l.titulo LIKE ? OR l.autor LIKE ? OR l.isbn LIKE ? ORDER BY l.titulo";

    /** Libros con ejemplares disponibles, para el combo del módulo de préstamos. */
    private static final String SQL_SELECT_DISPONIBLES = SQL_BASE
            + "WHERE l.stock > 0 ORDER BY l.titulo";

    private static final String SQL_INSERT =
            "INSERT INTO libros (titulo, autor, isbn, editorial, stock, id_categoria) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    /** Actualización de la ficha que deja el stock como esté en la base de datos. */
    private static final String SQL_UPDATE =
            "UPDATE libros SET titulo = ?, autor = ?, isbn = ?, editorial = ?, "
                    + "id_categoria = ? WHERE id = ?";

    /**
     * Actualización que también cambia el stock. El {@code AND stock = ?} es el
     * bloqueo optimista: solo escribe si el stock sigue siendo el que el formulario
     * leyó, de modo que un préstamo registrado entremedio no se pierda.
     */
    private static final String SQL_UPDATE_CON_STOCK =
            "UPDATE libros SET titulo = ?, autor = ?, isbn = ?, editorial = ?, "
                    + "stock = ?, id_categoria = ? WHERE id = ? AND stock = ?";

    private static final String SQL_DELETE = "DELETE FROM libros WHERE id = ?";

    /**
     * Guarda un libro nuevo. El identificador lo genera MySQL con
     * {@code AUTO_INCREMENT} y queda cargado en el libro recibido.
     *
     * @param libro libro que se quiere insertar
     * @return {@code true} si quedó guardado
     * @throws PersistenciaException si el ISBN ya está registrado, porque la columna
     *                               {@code isbn} es {@code UNIQUE}
     */
    @Override
    public boolean crear(Libro libro) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            asignarFicha(stmt, libro);
            stmt.setInt(5, libro.getStock());
            stmt.setInt(6, libro.getIdCategoria());

            if (stmt.executeUpdate() != 1) {
                return false;
            }

            try (ResultSet generadas = stmt.getGeneratedKeys()) {
                if (generadas.next()) {
                    libro.setId(generadas.getInt(1));
                }
            }
            return true;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "Ya existe un libro con el ISBN " + libro.getIsbn()
                            + ", o la categoría indicada no existe.", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al guardar el libro en la base de datos.", e);
        }
    }

    @Override
    public List<Libro> listar() {
        return consultar(SQL_SELECT);
    }

    /**
     * Lee los libros que tienen al menos un ejemplar disponible. Es la lista que se
     * ofrece al registrar un préstamo, para no proponer libros sin stock.
     *
     * @return los libros con stock mayor que cero
     * @throws PersistenciaException si la lectura falla
     */
    public List<Libro> listarDisponibles() {
        return consultar(SQL_SELECT_DISPONIBLES);
    }

    @Override
    public Libro buscarPorId(int id) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al buscar el libro #" + id + " en la base de datos.", e);
        }
    }

    /**
     * Busca libros cuyo título, autor o ISBN contenga el texto indicado.
     *
     * @param texto texto que escribió el usuario en el buscador
     * @return los libros que coinciden, vacío si no hay ninguno
     * @throws PersistenciaException si la consulta falla
     */
    public List<Libro> buscar(String texto) {
        List<Libro> encontrados = new ArrayList<>();
        String patron = "%" + texto + "%";

        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_BUSCAR)) {

            stmt.setString(1, patron);
            stmt.setString(2, patron);
            stmt.setString(3, patron);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    encontrados.add(mapear(rs));
                }
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al buscar libros en la base de datos.", e);
        }

        return encontrados;
    }

    /**
     * Guarda los datos de la ficha de un libro <b>dejando el stock intacto</b>.
     * <p>
     * Es la actualización que se usa cuando el bibliotecario no cambió el número de
     * ejemplares: al no escribir la columna {@code stock}, los préstamos y las
     * devoluciones que hayan ocurrido mientras la ficha estaba abierta se conservan.
     *
     * @param libro libro con los datos nuevos; su identificador indica la fila
     * @return {@code true} si se actualizó; {@code false} si el libro ya no existe
     * @throws PersistenciaException si el ISBN ya lo tiene otro libro o la
     *                               actualización falla
     */
    @Override
    public boolean actualizar(Libro libro) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE)) {

            asignarFicha(stmt, libro);
            stmt.setInt(5, libro.getIdCategoria());
            stmt.setInt(6, libro.getId());

            return stmt.executeUpdate() > 0;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "Otro libro ya tiene el ISBN " + libro.getIsbn()
                            + ", o la categoría indicada no existe.", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al actualizar el libro #" + libro.getId()
                            + " en la base de datos.", e);
        }
    }

    /**
     * Guarda los datos de la ficha de un libro <b>incluido el stock</b>, con bloqueo
     * optimista sobre esa columna.
     * <p>
     * El {@code UPDATE} solo se aplica si el stock guardado sigue siendo
     * {@code stockEsperado}, que es el que el formulario leyó al abrirse. Si no
     * coincide, significa que entremedio se registró un préstamo o una devolución: no
     * se escribe nada y se avisa con el stock real, para que el bibliotecario vuelva a
     * abrir la ficha y decida sobre el dato vigente. Es el mismo criterio del
     * {@code PrestamoDAO}, que tampoco descuenta un ejemplar si el stock cambió: antes
     * que guardar un número equivocado, se prefiere rechazar la operación.
     *
     * @param libro          libro con los datos nuevos; su identificador indica la fila
     * @param stockEsperado  stock que el formulario tenía cuando se abrió
     * @return {@code true} si se actualizó; {@code false} si el libro ya no existe
     * @throws PersistenciaException si el stock cambió mientras se editaba la ficha, si
     *                               el ISBN ya lo tiene otro libro o si la
     *                               actualización falla
     */
    public boolean actualizarConStock(Libro libro, int stockEsperado) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_CON_STOCK)) {

            asignarFicha(stmt, libro);
            stmt.setInt(5, libro.getStock());
            stmt.setInt(6, libro.getIdCategoria());
            stmt.setInt(7, libro.getId());
            stmt.setInt(8, stockEsperado);

            if (stmt.executeUpdate() > 0) {
                return true;
            }

            return informarConflicto(libro, stockEsperado);

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "Otro libro ya tiene el ISBN " + libro.getIsbn()
                            + ", o la categoría indicada no existe.", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al actualizar el libro #" + libro.getId()
                            + " en la base de datos.", e);
        }
    }

    /**
     * Averigua por qué el {@code UPDATE} con bloqueo optimista no actualizó ninguna
     * fila: o el libro ya no existe, o su stock cambió mientras se editaba la ficha.
     *
     * @param libro         libro que se intentó actualizar
     * @param stockEsperado stock que el formulario tenía cuando se abrió
     * @return {@code false} si el libro ya no existe en el catálogo
     * @throws PersistenciaException si el libro existe pero su stock ya no es el
     *                               esperado
     */
    private boolean informarConflicto(Libro libro, int stockEsperado) {
        Libro guardado = buscarPorId(libro.getId());
        if (guardado == null) {
            return false;
        }

        throw new PersistenciaException(
                "No se guardaron los cambios de \"" + guardado.getTitulo() + "\": su stock "
                        + "pasó de " + stockEsperado + " a " + guardado.getStock()
                        + " ejemplares mientras la ficha estaba abierta, porque se registró "
                        + "un préstamo o una devolución. Vuelva a abrir la ficha para "
                        + "modificarla sobre el stock actual.");
    }

    /**
     * Elimina un libro. MySQL lo rechaza si tiene préstamos registrados, porque
     * {@code prestamos.id_libro} es una clave foránea.
     *
     * @param id identificador del libro
     * @return {@code true} si se eliminó la fila; {@code false} si no existe
     * @throws PersistenciaException si el libro tiene préstamos registrados
     */
    @Override
    public boolean eliminar(int id) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {

            stmt.setInt(1, id);

            return stmt.executeUpdate() > 0;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "No se puede eliminar el libro porque tiene préstamos registrados. "
                            + "El historial de préstamos debe conservarse.", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al eliminar el libro #" + id + " en la base de datos.", e);
        }
    }

    /**
     * Ejecuta una consulta que devuelve libros y los convierte en objetos.
     *
     * @param sql consulta sin parámetros
     * @return los libros leídos
     * @throws PersistenciaException si la lectura falla
     */
    private List<Libro> consultar(String sql) {
        List<Libro> libros = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                libros.add(mapear(rs));
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al cargar los libros desde la base de datos.", e);
        }

        return libros;
    }

    /**
     * Carga en el {@link PreparedStatement} los cuatro datos de texto del libro, que
     * son los primeros parámetros y en el mismo orden en el {@code INSERT} y en las dos
     * versiones del {@code UPDATE}. Lo que cambia entre esas sentencias —el stock, la
     * categoría, el identificador— lo agrega cada método según su SQL.
     *
     * @param stmt  sentencia que se está preparando
     * @param libro libro con los datos
     * @throws SQLException si la sentencia rechaza algún parámetro
     */
    private void asignarFicha(PreparedStatement stmt, Libro libro) throws SQLException {
        stmt.setString(1, libro.getTitulo());
        stmt.setString(2, libro.getAutor());
        stmt.setString(3, libro.getIsbn());
        stmt.setString(4, libro.getEditorial());
    }

    /**
     * Convierte la fila actual del {@link ResultSet} en un {@link Libro}, incluido el
     * nombre de la categoría que trajo el {@code JOIN}.
     *
     * @param rs resultado posicionado en la fila que se quiere leer
     * @return el libro leído
     * @throws SQLException si alguna columna no se puede leer
     */
    private Libro mapear(ResultSet rs) throws SQLException {
        Libro libro = new Libro(
                rs.getInt("id"),
                rs.getString("titulo"),
                rs.getString("autor"),
                rs.getString("isbn"),
                rs.getString("editorial"),
                rs.getInt("stock"),
                rs.getInt("id_categoria"));
        libro.setNombreCategoria(rs.getString("categoria"));
        return libro;
    }
}
