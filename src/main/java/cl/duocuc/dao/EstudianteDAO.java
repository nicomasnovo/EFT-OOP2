package cl.duocuc.dao;

import cl.duocuc.conexion.DatabaseConnection;
import cl.duocuc.exception.PersistenciaException;
import cl.duocuc.model.Estudiante;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code estudiantes}. Implementa {@link CrudDAO} con el
 * CRUD completo de los estudiantes de la biblioteca.
 * <p>
 * Las conexiones se piden siempre al Singleton
 * {@link DatabaseConnection#obtenerConexion()} y se cierran con
 * try-with-resources. El flujo completo es
 * vista → controlador → DAO → DatabaseConnection → MySQL.
 * <p>
 * Este DAO no depende de Swing: cuando una operación falla lanza una
 * {@link PersistenciaException} con un mensaje entendible y es la vista la que lo
 * muestra con {@code JOptionPane}.
 */
public class EstudianteDAO implements CrudDAO<Estudiante> {

    private static final String SQL_SELECT =
            "SELECT id, nombre, rut, curso, correo FROM estudiantes ORDER BY nombre";

    private static final String SQL_SELECT_BY_ID =
            "SELECT id, nombre, rut, curso, correo FROM estudiantes WHERE id = ?";

    private static final String SQL_SELECT_BY_RUT =
            "SELECT id, nombre, rut, curso, correo FROM estudiantes WHERE rut = ?";

    /** Búsqueda por nombre, RUT o curso, usada por el buscador del módulo. */
    private static final String SQL_BUSCAR =
            "SELECT id, nombre, rut, curso, correo FROM estudiantes "
                    + "WHERE nombre LIKE ? OR rut LIKE ? OR curso LIKE ? ORDER BY nombre";

    private static final String SQL_INSERT =
            "INSERT INTO estudiantes (nombre, rut, curso, correo) VALUES (?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE estudiantes SET nombre = ?, rut = ?, curso = ?, correo = ? WHERE id = ?";

    private static final String SQL_DELETE = "DELETE FROM estudiantes WHERE id = ?";

    /**
     * Guarda un estudiante nuevo. El identificador lo genera MySQL con
     * {@code AUTO_INCREMENT} y queda cargado en el estudiante recibido.
     *
     * @param estudiante estudiante que se quiere insertar
     * @return {@code true} si quedó guardado
     * @throws PersistenciaException si el RUT ya está registrado, porque la columna
     *                               {@code rut} es {@code UNIQUE}
     */
    @Override
    public boolean crear(Estudiante estudiante) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            asignarDatos(stmt, estudiante);

            if (stmt.executeUpdate() != 1) {
                return false;
            }

            try (ResultSet generadas = stmt.getGeneratedKeys()) {
                if (generadas.next()) {
                    estudiante.setId(generadas.getInt(1));
                }
            }
            return true;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "Ya existe un estudiante con el RUT " + estudiante.getRut() + ".", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al guardar el estudiante en la base de datos.", e);
        }
    }

    @Override
    public List<Estudiante> listar() {
        return consultar(SQL_SELECT);
    }

    @Override
    public Estudiante buscarPorId(int id) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al buscar el estudiante #" + id + " en la base de datos.", e);
        }
    }

    /**
     * Busca la ficha del estudiante que corresponde a un RUT. Se usa al iniciar
     * sesión con el rol {@code estudiante}: las tablas {@code usuarios} y
     * {@code estudiantes} son independientes en el script oficial, y el RUT es el
     * dato que las vincula porque es {@code UNIQUE} en ambas.
     *
     * @param rut RUT del usuario que inició sesión
     * @return la ficha encontrada, o {@code null} si ese RUT no está en la tabla
     * @throws PersistenciaException si la consulta falla
     */
    public Estudiante buscarPorRut(String rut) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_BY_RUT)) {

            stmt.setString(1, rut);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al buscar el estudiante con RUT " + rut
                            + " en la base de datos.", e);
        }
    }

    /**
     * Busca estudiantes cuyo nombre, RUT o curso contenga el texto indicado.
     *
     * @param texto texto que escribió el usuario en el buscador
     * @return los estudiantes que coinciden, vacío si no hay ninguno
     * @throws PersistenciaException si la consulta falla
     */
    public List<Estudiante> buscar(String texto) {
        List<Estudiante> encontrados = new ArrayList<>();
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
                    "Error al buscar estudiantes en la base de datos.", e);
        }

        return encontrados;
    }

    @Override
    public boolean actualizar(Estudiante estudiante) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE)) {

            asignarDatos(stmt, estudiante);
            stmt.setInt(5, estudiante.getId());

            return stmt.executeUpdate() > 0;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "Otro estudiante ya tiene el RUT " + estudiante.getRut() + ".", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al actualizar el estudiante #" + estudiante.getId()
                            + " en la base de datos.", e);
        }
    }

    /**
     * Elimina un estudiante. MySQL lo rechaza si tiene préstamos registrados,
     * porque {@code prestamos.id_estudiante} es una clave foránea.
     *
     * @param id identificador del estudiante
     * @return {@code true} si se eliminó la fila; {@code false} si no existe
     * @throws PersistenciaException si el estudiante tiene préstamos registrados
     */
    @Override
    public boolean eliminar(int id) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {

            stmt.setInt(1, id);

            return stmt.executeUpdate() > 0;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "No se puede eliminar el estudiante porque tiene préstamos "
                            + "registrados en su historial.", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al eliminar el estudiante #" + id + " en la base de datos.", e);
        }
    }

    /**
     * Ejecuta una consulta que devuelve estudiantes y los convierte en objetos.
     *
     * @param sql consulta sin parámetros
     * @return los estudiantes leídos
     * @throws PersistenciaException si la lectura falla
     */
    private List<Estudiante> consultar(String sql) {
        List<Estudiante> estudiantes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                estudiantes.add(mapear(rs));
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al cargar los estudiantes desde la base de datos.", e);
        }

        return estudiantes;
    }

    /**
     * Carga en el {@link PreparedStatement} los cuatro datos del estudiante, que son
     * los mismos y en el mismo orden en el {@code INSERT} y en el {@code UPDATE}.
     *
     * @param stmt       sentencia que se está preparando
     * @param estudiante estudiante con los datos
     * @throws SQLException si la sentencia rechaza algún parámetro
     */
    private void asignarDatos(PreparedStatement stmt, Estudiante estudiante) throws SQLException {
        stmt.setString(1, estudiante.getNombre());
        stmt.setString(2, estudiante.getRut());
        stmt.setString(3, estudiante.getCurso());
        stmt.setString(4, estudiante.getCorreo());
    }

    /**
     * Convierte la fila actual del {@link ResultSet} en un {@link Estudiante}.
     *
     * @param rs resultado posicionado en la fila que se quiere leer
     * @return el estudiante leído
     * @throws SQLException si alguna columna no se puede leer
     */
    private Estudiante mapear(ResultSet rs) throws SQLException {
        return new Estudiante(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("rut"),
                rs.getString("curso"),
                rs.getString("correo"));
    }
}
