package cl.duocuc.dao;

import cl.duocuc.conexion.DatabaseConnection;
import cl.duocuc.exception.PersistenciaException;
import cl.duocuc.model.Prestamo;
import cl.duocuc.model.Reporte;
import cl.duocuc.model.ResultadoPrestamo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code prestamos}. Concentra el registro de préstamos
 * y devoluciones, las consultas para las tablas de la interfaz y los tres reportes.
 * <p>
 * No implementa {@link CrudDAO} a propósito: un préstamo no se edita ni se borra
 * desde la aplicación, porque es el historial de la biblioteca. Las dos operaciones
 * que lo modifican —prestar y devolver— no son un {@code INSERT} ni un
 * {@code UPDATE} suelto, sino <b>transacciones JDBC</b>: el préstamo y el stock del
 * libro deben cambiar juntos o no cambiar.
 * <p>
 * Por eso ambas abren la conexión con {@code setAutoCommit(false)}, leen la fila del
 * libro con {@code SELECT ... FOR UPDATE} para que MySQL la bloquee mientras dura la
 * transacción, hacen sus dos sentencias y terminan con {@code commit()}. Si algo
 * falla, {@code rollback()} deja la base como estaba y el {@code finally} restaura
 * el auto-commit antes de cerrar la conexión.
 */
public class PrestamoDAO {

    /** Columnas comunes a las lecturas, con el nombre del estudiante y el título del libro. */
    private static final String SQL_BASE =
            "SELECT p.id, p.id_estudiante, p.id_libro, p.fecha_prestamo, "
                    + "p.fecha_devolucion, p.devuelto, e.nombre AS estudiante, "
                    + "l.titulo AS libro "
                    + "FROM prestamos p "
                    + "JOIN estudiantes e ON e.id = p.id_estudiante "
                    + "JOIN libros l ON l.id = p.id_libro ";

    /** Préstamos todavía no devueltos, que son los que se pueden devolver. */
    private static final String SQL_SELECT_PENDIENTES = SQL_BASE
            + "WHERE p.devuelto = FALSE ORDER BY p.fecha_devolucion";

    /** Historial completo de un estudiante, del préstamo más reciente al más antiguo. */
    private static final String SQL_SELECT_POR_ESTUDIANTE = SQL_BASE
            + "WHERE p.id_estudiante = ? ORDER BY p.fecha_prestamo DESC";

    private static final String SQL_INSERT =
            "INSERT INTO prestamos (id_estudiante, id_libro, fecha_prestamo, "
                    + "fecha_devolucion, devuelto) VALUES (?, ?, ?, ?, FALSE)";

    /**
     * Lee el stock del libro y bloquea su fila hasta el {@code commit}, de modo que
     * dos préstamos simultáneos del mismo libro no lean el mismo stock.
     */
    private static final String SQL_STOCK_PARA_ACTUALIZAR =
            "SELECT stock FROM libros WHERE id = ? FOR UPDATE";

    private static final String SQL_DESCONTAR_STOCK =
            "UPDATE libros SET stock = stock - 1 WHERE id = ? AND stock > 0";

    private static final String SQL_AUMENTAR_STOCK =
            "UPDATE libros SET stock = stock + 1 WHERE id = ?";

    /**
     * Busca un préstamo pendiente del mismo libro al mismo estudiante y bloquea lo que
     * encuentre, para que dos intentos simultáneos no puedan colarse los dos.
     */
    private static final String SQL_PRESTAMO_PENDIENTE_DEL_MISMO_LIBRO =
            "SELECT id FROM prestamos "
                    + "WHERE id_estudiante = ? AND id_libro = ? AND devuelto = FALSE "
                    + "FOR UPDATE";

    /** Lee el préstamo que se quiere devolver y bloquea su fila hasta el {@code commit}. */
    private static final String SQL_PRESTAMO_PARA_ACTUALIZAR =
            "SELECT id_libro, devuelto FROM prestamos WHERE id = ? FOR UPDATE";

    private static final String SQL_MARCAR_DEVUELTO =
            "UPDATE prestamos SET devuelto = TRUE WHERE id = ? AND devuelto = FALSE";

    /** Reporte 1: ranking de libros por cantidad de préstamos. */
    private static final String SQL_LIBROS_MAS_PRESTADOS =
            "SELECT l.titulo, l.autor, COUNT(p.id) AS veces "
                    + "FROM prestamos p JOIN libros l ON l.id = p.id_libro "
                    + "GROUP BY l.id, l.titulo, l.autor "
                    + "ORDER BY veces DESC, l.titulo";

    /** Reporte 2: historial de préstamos de un estudiante. */
    private static final String SQL_HISTORIAL_ESTUDIANTE =
            "SELECT l.titulo, l.autor, p.fecha_prestamo, p.fecha_devolucion, p.devuelto "
                    + "FROM prestamos p JOIN libros l ON l.id = p.id_libro "
                    + "WHERE p.id_estudiante = ? ORDER BY p.fecha_prestamo DESC";

    /** Reporte 3: libros que están fuera de la biblioteca en este momento. */
    private static final String SQL_LIBROS_PRESTADOS =
            "SELECT l.titulo, e.nombre, e.curso, p.fecha_prestamo, p.fecha_devolucion "
                    + "FROM prestamos p "
                    + "JOIN libros l ON l.id = p.id_libro "
                    + "JOIN estudiantes e ON e.id = p.id_estudiante "
                    + "WHERE p.devuelto = FALSE ORDER BY p.fecha_devolucion";

    /**
     * Registra un préstamo y descuenta un ejemplar del libro, todo en una sola
     * transacción.
     * <p>
     * El orden de los pasos es parte del control de consistencia: primero revisa con
     * {@code FOR UPDATE} que el estudiante no tenga ya ese libro pendiente, después
     * bloquea la fila del libro y comprueba que quede stock, y solo entonces inserta el
     * préstamo y descuenta el ejemplar. Las dos reglas se verifican <b>dentro</b> de la
     * transacción, con las filas bloqueadas, porque comprobarlas antes no serviría: otro
     * préstamo simultáneo podría cambiar justo lo recién revisado. Si alguna no se
     * cumple, {@code rollback()} deja la base como estaba y el método informa el motivo
     * sin haber insertado nada.
     * <p>
     * Las tablas se bloquean en el mismo orden que en {@link #registrarDevolucion(int)}
     * —primero {@code prestamos}, después {@code libros}— para que un préstamo y una
     * devolución simultáneos no se queden esperando uno al otro.
     *
     * @param prestamo préstamo por registrar, con su fecha de devolución ya calculada
     * @return cómo terminó el intento: registrado, sin ejemplares o ya prestado
     * @throws PersistenciaException si el libro no existe o la transacción falla
     */
    public ResultadoPrestamo registrarPrestamo(Prestamo prestamo) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().obtenerConexion();
            conn.setAutoCommit(false);

            if (yaLoTienePrestado(conn, prestamo)) {
                conn.rollback();
                return ResultadoPrestamo.YA_LO_TIENE_PRESTADO;
            }

            int stock = leerStockBloqueando(conn, prestamo.getIdLibro());
            if (stock < 0) {
                conn.rollback();
                throw new PersistenciaException(
                        "El libro seleccionado ya no existe en el catálogo.");
            }
            if (stock == 0) {
                conn.rollback();
                return ResultadoPrestamo.SIN_EJEMPLARES;
            }

            insertarPrestamo(conn, prestamo);

            try (PreparedStatement stmt = conn.prepareStatement(SQL_DESCONTAR_STOCK)) {
                stmt.setInt(1, prestamo.getIdLibro());
                if (stmt.executeUpdate() != 1) {
                    conn.rollback();
                    return ResultadoPrestamo.SIN_EJEMPLARES;
                }
            }

            conn.commit();
            return ResultadoPrestamo.REGISTRADO;

        } catch (SQLException e) {
            deshacer(conn);
            throw new PersistenciaException(
                    "Error al registrar el préstamo en la base de datos. "
                            + "No se guardó ningún cambio.", e);
        } catch (PersistenciaException e) {
            deshacer(conn);
            throw e;
        } finally {
            cerrar(conn);
        }
    }

    /**
     * Marca un préstamo como devuelto y devuelve el ejemplar al stock del libro, en
     * una sola transacción.
     * <p>
     * Antes de escribir lee el préstamo con {@code FOR UPDATE} y comprueba que no
     * esté devuelto, de modo que una devolución no se pueda registrar dos veces y el
     * stock no suba de más.
     *
     * @param idPrestamo identificador del préstamo que se devuelve
     * @return {@code true} si quedó registrada la devolución; {@code false} si el
     *         préstamo ya estaba devuelto
     * @throws PersistenciaException si el préstamo no existe o la transacción falla
     */
    public boolean registrarDevolucion(int idPrestamo) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().obtenerConexion();
            conn.setAutoCommit(false);

            int idLibro;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_PRESTAMO_PARA_ACTUALIZAR)) {
                stmt.setInt(1, idPrestamo);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        throw new PersistenciaException(
                                "El préstamo #" + idPrestamo + " ya no existe.");
                    }
                    if (rs.getBoolean("devuelto")) {
                        conn.rollback();
                        return false;
                    }
                    idLibro = rs.getInt("id_libro");
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(SQL_MARCAR_DEVUELTO)) {
                stmt.setInt(1, idPrestamo);
                if (stmt.executeUpdate() != 1) {
                    conn.rollback();
                    return false;
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(SQL_AUMENTAR_STOCK)) {
                stmt.setInt(1, idLibro);
                stmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            deshacer(conn);
            throw new PersistenciaException(
                    "Error al registrar la devolución en la base de datos. "
                            + "No se guardó ningún cambio.", e);
        } catch (PersistenciaException e) {
            deshacer(conn);
            throw e;
        } finally {
            cerrar(conn);
        }
    }

    /**
     * Lee los préstamos que todavía no se han devuelto.
     *
     * @return los préstamos pendientes, ordenados por fecha de devolución
     * @throws PersistenciaException si la lectura falla
     */
    public List<Prestamo> listarPendientes() {
        List<Prestamo> pendientes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_PENDIENTES);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                pendientes.add(mapear(rs));
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al cargar los préstamos pendientes desde la base de datos.", e);
        }

        return pendientes;
    }

    /**
     * Lee todos los préstamos de un estudiante, devueltos y pendientes.
     *
     * @param idEstudiante identificador del estudiante
     * @return sus préstamos, del más reciente al más antiguo
     * @throws PersistenciaException si la lectura falla
     */
    public List<Prestamo> listarPorEstudiante(int idEstudiante) {
        List<Prestamo> prestamos = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_POR_ESTUDIANTE)) {

            stmt.setInt(1, idEstudiante);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    prestamos.add(mapear(rs));
                }
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al cargar los préstamos del estudiante desde la base de datos.", e);
        }

        return prestamos;
    }

    /**
     * Reporte de los libros más prestados, con la cantidad de veces que salió cada
     * uno.
     *
     * @return el reporte con sus encabezados y filas
     * @throws PersistenciaException si la consulta falla
     */
    public Reporte librosMasPrestados() {
        return consultarReporte(SQL_LIBROS_MAS_PRESTADOS,
                new String[]{"Título", "Autor", "Veces prestado"}, 0);
    }

    /**
     * Reporte con el historial de préstamos de un estudiante.
     *
     * @param idEstudiante identificador del estudiante
     * @return el reporte con sus encabezados y filas
     * @throws PersistenciaException si la consulta falla
     */
    public Reporte historialEstudiante(int idEstudiante) {
        return consultarReporte(SQL_HISTORIAL_ESTUDIANTE,
                new String[]{"Título", "Autor", "Fecha préstamo", "Fecha devolución", "Estado"},
                idEstudiante);
    }

    /**
     * Reporte de los libros que están prestados en este momento, con quién los tiene.
     *
     * @return el reporte con sus encabezados y filas
     * @throws PersistenciaException si la consulta falla
     */
    public Reporte librosPrestados() {
        return consultarReporte(SQL_LIBROS_PRESTADOS,
                new String[]{"Título", "Estudiante", "Curso", "Fecha préstamo", "Devolver antes de"},
                0);
    }

    /**
     * Ejecuta una consulta de reporte y arma el {@link Reporte} con sus filas.
     * Convierte a texto los valores que necesitan formato propio —las fechas y el
     * {@code BOOLEAN devuelto}— para que la vista solo tenga que mostrarlos.
     *
     * @param sql          consulta del reporte
     * @param columnas     encabezados que se mostrarán en el {@code JTable}
     * @param idEstudiante identificador que filtra la consulta, o {@code 0} si la
     *                     consulta no lleva parámetros
     * @return el reporte leído
     * @throws PersistenciaException si la consulta falla
     */
    private Reporte consultarReporte(String sql, String[] columnas, int idEstudiante) {
        List<Object[]> filas = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (idEstudiante > 0) {
                stmt.setInt(1, idEstudiante);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                int cantidad = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] fila = new Object[cantidad];
                    for (int columna = 1; columna <= cantidad; columna++) {
                        fila[columna - 1] = valorLegible(rs, columna);
                    }
                    filas.add(fila);
                }
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al generar el reporte desde la base de datos.", e);
        }

        return new Reporte(columnas, filas);
    }

    /**
     * Devuelve el valor de una columna del reporte en el formato en que se muestra:
     * las fechas como {@code aaaa-mm-dd} y el {@code devuelto} como "Devuelto" o
     * "Pendiente".
     *
     * @param rs      resultado posicionado en la fila que se está leyendo
     * @param columna número de columna
     * @return el valor listo para mostrarse
     * @throws SQLException si la columna no se puede leer
     */
    private Object valorLegible(ResultSet rs, int columna) throws SQLException {
        Object valor = rs.getObject(columna);
        if (valor instanceof Date fecha) {
            return fecha.toLocalDate().toString();
        }
        if (valor instanceof Boolean devuelto) {
            return devuelto ? "Devuelto" : "Pendiente";
        }
        return valor;
    }

    /**
     * Comprueba si el estudiante ya tiene ese mismo libro prestado y sin devolver,
     * bloqueando lo que encuentre hasta el final de la transacción.
     * <p>
     * El {@code FOR UPDATE} es lo que hace confiable la regla: si dos préstamos iguales
     * entran a la vez, el segundo espera a que el primero confirme y entonces sí ve su
     * fila, en vez de que los dos lean "no hay ninguno" y se guarden los dos.
     *
     * @param conn     conexión de la transacción en curso
     * @param prestamo préstamo que se está intentando registrar
     * @return {@code true} si ya hay un préstamo pendiente de ese libro a ese estudiante
     * @throws SQLException si la consulta falla
     */
    private boolean yaLoTienePrestado(Connection conn, Prestamo prestamo) throws SQLException {
        try (PreparedStatement stmt =
                     conn.prepareStatement(SQL_PRESTAMO_PENDIENTE_DEL_MISMO_LIBRO)) {

            stmt.setInt(1, prestamo.getIdEstudiante());
            stmt.setInt(2, prestamo.getIdLibro());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Lee el stock actual de un libro bloqueando su fila hasta el final de la
     * transacción.
     *
     * @param conn    conexión de la transacción en curso
     * @param idLibro identificador del libro
     * @return el stock disponible, o {@code -1} si el libro no existe
     * @throws SQLException si la consulta falla
     */
    private int leerStockBloqueando(Connection conn, int idLibro) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_STOCK_PARA_ACTUALIZAR)) {
            stmt.setInt(1, idLibro);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("stock") : -1;
            }
        }
    }

    /**
     * Inserta el préstamo dentro de la transacción y guarda en el objeto el
     * identificador que generó MySQL.
     *
     * @param conn     conexión de la transacción en curso
     * @param prestamo préstamo que se inserta
     * @throws SQLException si la inserción falla
     */
    private void insertarPrestamo(Connection conn, Prestamo prestamo) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT,
                PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, prestamo.getIdEstudiante());
            stmt.setInt(2, prestamo.getIdLibro());
            stmt.setDate(3, Date.valueOf(prestamo.getFechaPrestamo()));
            stmt.setDate(4, Date.valueOf(prestamo.getFechaDevolucion()));
            stmt.executeUpdate();

            try (ResultSet generadas = stmt.getGeneratedKeys()) {
                if (generadas.next()) {
                    prestamo.setId(generadas.getInt(1));
                }
            }
        }
    }

    /**
     * Deshace la transacción cuando una operación falla, para que el préstamo y el
     * stock no queden descuadrados.
     *
     * @param conn conexión de la transacción, o {@code null} si no se alcanzó a abrir
     */
    private void deshacer(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException e) {
            System.err.println("No se pudo deshacer la transacción: " + e.getMessage());
        }
    }

    /**
     * Restaura el auto-commit y cierra la conexión de la transacción. Se restaura
     * antes de cerrar porque la conexión se pidió con {@code setAutoCommit(false)} y
     * así se devuelve en el mismo estado en que la entrega el Singleton.
     *
     * @param conn conexión de la transacción, o {@code null} si no se alcanzó a abrir
     */
    private void cerrar(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println("No se pudo restaurar el auto-commit: " + e.getMessage());
        }
        try {
            conn.close();
        } catch (SQLException e) {
            System.err.println("No se pudo cerrar la conexión: " + e.getMessage());
        }
    }

    /**
     * Convierte la fila actual del {@link ResultSet} en un {@link Prestamo}, con el
     * nombre del estudiante y el título del libro que trajo el {@code JOIN}.
     *
     * @param rs resultado posicionado en la fila que se quiere leer
     * @return el préstamo leído
     * @throws SQLException si alguna columna no se puede leer
     */
    private Prestamo mapear(ResultSet rs) throws SQLException {
        Date prestado = rs.getDate("fecha_prestamo");
        Date devolver = rs.getDate("fecha_devolucion");

        Prestamo prestamo = new Prestamo(
                rs.getInt("id"),
                rs.getInt("id_estudiante"),
                rs.getInt("id_libro"),
                prestado == null ? null : prestado.toLocalDate(),
                devolver == null ? null : devolver.toLocalDate(),
                rs.getBoolean("devuelto"));
        prestamo.setNombreEstudiante(rs.getString("estudiante"));
        prestamo.setTituloLibro(rs.getString("libro"));
        return prestamo;
    }
}
