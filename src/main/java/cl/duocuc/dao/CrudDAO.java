package cl.duocuc.dao;

import cl.duocuc.exception.PersistenciaException;

import java.util.List;

/**
 * Operaciones de mantención que comparten los DAO de las entidades administrables
 * de la biblioteca: categorías, libros y estudiantes.
 * <p>
 * Es la <b>interfaz</b> del proyecto y define el contrato del CRUD sin decir nada
 * sobre SQL. Cada DAO la implementa con las consultas de su propia tabla, y el
 * {@code CrudController} trabaja solamente contra este tipo: cuando llama a
 * {@code listar()} o {@code eliminar()} no sabe si detrás hay libros, estudiantes o
 * categorías. Ahí está el <b>polimorfismo</b>: una misma llamada ejecuta tres
 * implementaciones distintas.
 *
 * @param <T> entidad del modelo que administra el DAO
 */
public interface CrudDAO<T> {

    /**
     * Guarda una entidad nueva.
     *
     * @param entidad entidad que se quiere insertar
     * @return {@code true} si quedó guardada
     * @throws PersistenciaException si la base de datos rechaza la inserción
     */
    boolean crear(T entidad);

    /**
     * Lee todas las filas de la tabla.
     *
     * @return la lista de entidades, vacía si la tabla no tiene filas
     * @throws PersistenciaException si la lectura falla
     */
    List<T> listar();

    /**
     * Busca una entidad por su identificador.
     *
     * @param id identificador de la fila
     * @return la entidad encontrada, o {@code null} si no existe
     * @throws PersistenciaException si la lectura falla
     */
    T buscarPorId(int id);

    /**
     * Guarda los datos nuevos de una entidad ya existente.
     *
     * @param entidad entidad con los datos nuevos; su identificador indica la fila
     * @return {@code true} si se actualizó alguna fila; {@code false} si no existe
     * @throws PersistenciaException si la base de datos rechaza la actualización
     */
    boolean actualizar(T entidad);

    /**
     * Elimina una entidad.
     *
     * @param id identificador de la fila que se quiere eliminar
     * @return {@code true} si se eliminó la fila; {@code false} si no existe
     * @throws PersistenciaException si la base de datos rechaza la eliminación, por
     *                               ejemplo por integridad referencial
     */
    boolean eliminar(int id);
}
