package cl.duocuc.dao;

import cl.duocuc.conexion.DatabaseConnection;
import cl.duocuc.exception.PersistenciaException;
import cl.duocuc.model.Rol;
import cl.duocuc.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a datos de la tabla {@code usuarios}. Su única responsabilidad es
 * autenticar: busca al usuario por su correo y compara la contraseña guardada en
 * la base de datos.
 * <p>
 * No implementa {@link CrudDAO} porque el sistema no administra usuarios desde la
 * interfaz: las credenciales son las que trae el script de poblado, y por eso no
 * hay ningún usuario escrito en el código.
 * <p>
 * La columna de la contraseña se llama {@code contraseña} en el script oficial. Al
 * consultarla se le pone el alias {@code contrasena} para que el resto del código
 * Java no dependa de un nombre con eñe.
 */
public class UsuarioDAO {

    /** Consulta que busca un usuario por el correo con que inicia sesión. */
    private static final String SQL_SELECT_POR_CORREO =
            "SELECT id, nombre, rut, correo, contraseña AS contrasena, rol "
                    + "FROM usuarios WHERE correo = ?";

    /**
     * Busca al usuario dueño de un correo y comprueba su contraseña.
     *
     * @param correo     correo ingresado en el login
     * @param contrasena contraseña ingresada en el login
     * @return el usuario autenticado con su rol, o {@code null} si el correo no
     *         existe o la contraseña no coincide
     * @throws PersistenciaException si la consulta falla o el rol guardado no es válido
     */
    public Usuario autenticar(String correo, String contrasena) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_POR_CORREO)) {

            stmt.setString(1, correo);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                Usuario usuario = mapear(rs);
                return usuario.getContrasena() != null
                        && usuario.getContrasena().equals(contrasena) ? usuario : null;
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al validar las credenciales en la base de datos.", e);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException(
                    "El usuario tiene un rol no válido en la base de datos: "
                            + e.getMessage(), e);
        }
    }

    /**
     * Convierte la fila actual del {@link ResultSet} en un {@link Usuario}.
     *
     * @param rs resultado posicionado en la fila que se quiere leer
     * @return el usuario leído
     * @throws SQLException             si alguna columna no se puede leer
     * @throws IllegalArgumentException si el rol guardado no corresponde a {@link Rol}
     */
    private Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("rut"),
                rs.getString("correo"),
                rs.getString("contrasena"),
                Rol.desdeTexto(rs.getString("rol")));
    }
}
