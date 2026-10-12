package cl.duocuc.dao;

import cl.duocuc.conexion.DatabaseConnection;
import cl.duocuc.exception.PersistenciaException;
import cl.duocuc.model.Categoria;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code categorias}. Implementa {@link CrudDAO} con las
 * consultas de su tabla, que solo tiene identificador y nombre.
 * <p>
 * Las categorías alimentan el {@code JComboBox} del módulo de libros, de modo que
 * el bibliotecario elija "Historia" y sea este DAO el que entregue el
 * {@code id_categoria} que necesita la tabla {@code libros}.
 */
public class CategoriaDAO implements CrudDAO<Categoria> {

    private static final String SQL_SELECT = "SELECT id, nombre FROM categorias ORDER BY nombre";

    private static final String SQL_SELECT_BY_ID = "SELECT id, nombre FROM categorias WHERE id = ?";

    private static final String SQL_INSERT = "INSERT INTO categorias (nombre) VALUES (?)";

    private static final String SQL_UPDATE = "UPDATE categorias SET nombre = ? WHERE id = ?";

    private static final String SQL_DELETE = "DELETE FROM categorias WHERE id = ?";

    /**
     * Guarda una categoría nueva. El identificador lo genera MySQL con
     * {@code AUTO_INCREMENT} y se devuelve en la misma categoría recibida.
     *
     * @param categoria categoría que se quiere insertar
     * @return {@code true} si quedó guardada
     * @throws PersistenciaException si la base de datos rechaza la inserción
     */
    @Override
    public boolean crear(Categoria categoria) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, categoria.getNombre());

            if (stmt.executeUpdate() != 1) {
                return false;
            }

            try (ResultSet generadas = stmt.getGeneratedKeys()) {
                if (generadas.next()) {
                    categoria.setId(generadas.getInt(1));
                }
            }
            return true;

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al guardar la categoría en la base de datos.", e);
        }
    }

    @Override
    public List<Categoria> listar() {
        List<Categoria> categorias = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                categorias.add(mapear(rs));
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al cargar las categorías desde la base de datos.", e);
        }

        return categorias;
    }

    @Override
    public Categoria buscarPorId(int id) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al buscar la categoría #" + id + " en la base de datos.", e);
        }
    }

    @Override
    public boolean actualizar(Categoria categoria) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE)) {

            stmt.setString(1, categoria.getNombre());
            stmt.setInt(2, categoria.getId());

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al actualizar la categoría #" + categoria.getId()
                            + " en la base de datos.", e);
        }
    }

    /**
     * Elimina una categoría. MySQL la rechaza si todavía tiene libros asociados,
     * porque {@code libros.id_categoria} es una clave foránea.
     *
     * @param id identificador de la categoría
     * @return {@code true} si se eliminó la fila; {@code false} si no existe
     * @throws PersistenciaException si la categoría tiene libros asociados
     */
    @Override
    public boolean eliminar(int id) {
        try (Connection conn = DatabaseConnection.getInstance().obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {

            stmt.setInt(1, id);

            return stmt.executeUpdate() > 0;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new PersistenciaException(
                    "No se puede eliminar la categoría porque tiene libros asociados. "
                            + "Cambie primero la categoría de esos libros.", e);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Error al eliminar la categoría #" + id + " en la base de datos.", e);
        }
    }

    /**
     * Convierte la fila actual del {@link ResultSet} en una {@link Categoria}.
     *
     * @param rs resultado posicionado en la fila que se quiere leer
     * @return la categoría leída
     * @throws SQLException si alguna columna no se puede leer
     */
    private Categoria mapear(ResultSet rs) throws SQLException {
        return new Categoria(rs.getInt("id"), rs.getString("nombre"));
    }
}
