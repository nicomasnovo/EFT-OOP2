package cl.duocuc.controllers;

import cl.duocuc.dao.CrudDAO;

import java.util.List;

/**
 * Base de los controladores que administran una entidad con CRUD completo: libros,
 * estudiantes y categorías.
 * <p>
 * Guarda el {@link CrudDAO} de su entidad y resuelve con él las tres operaciones que
 * se hacen igual en todos los módulos: listar, buscar por identificador y eliminar.
 * Lo hace sin saber qué entidad es, porque solo conoce la interfaz: la misma línea
 * {@code dao.listar()} ejecuta el SQL de {@code libros}, de {@code estudiantes} o de
 * {@code categorias} según la implementación que recibió. Ahí está el polimorfismo.
 * <p>
 * Lo que cambia entre módulos —qué campos se validan al crear o al modificar— queda
 * en cada subclase, que es la que arma el objeto del modelo y llama al DAO.
 *
 * @param <T> entidad del modelo que administra el controlador
 */
public abstract class CrudController<T> {

    private final CrudDAO<T> dao;

    /**
     * Crea el controlador con el DAO de su entidad.
     *
     * @param dao acceso a datos de la entidad administrada
     */
    protected CrudController(CrudDAO<T> dao) {
        this.dao = dao;
    }

    /**
     * Devuelve todos los registros de la entidad para llenar la tabla del módulo.
     *
     * @return la lista de registros
     */
    public List<T> listar() {
        return dao.listar();
    }

    /**
     * Busca un registro por su identificador.
     *
     * @param id identificador del registro
     * @return el registro encontrado, o {@code null} si no existe
     */
    public T buscarPorId(int id) {
        return dao.buscarPorId(id);
    }

    /**
     * Elimina el registro seleccionado.
     *
     * @param id identificador del registro
     * @return {@code true} si se eliminó; {@code false} si ya no existía
     * @throws IllegalArgumentException si no hay un registro seleccionado
     */
    public boolean eliminar(int id) {
        Validaciones.identificador(id, "un registro de la tabla");
        return dao.eliminar(id);
    }

    /**
     * Da acceso al DAO a las subclases, que lo usan para crear y actualizar con los
     * datos ya validados.
     *
     * @return el DAO de la entidad
     */
    protected CrudDAO<T> getDao() {
        return dao;
    }
}
