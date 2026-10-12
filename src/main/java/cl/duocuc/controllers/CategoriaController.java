package cl.duocuc.controllers;

import cl.duocuc.dao.CategoriaDAO;
import cl.duocuc.model.Categoria;

/**
 * Controlador del módulo de categorías. Hereda de {@link CrudController} el listado,
 * la búsqueda por identificador y la eliminación, y agrega la creación y la
 * modificación con sus validaciones.
 * <p>
 * Las categorías existen para administrar los libros: son las que llenan el
 * {@code JComboBox} del módulo de libros, de modo que el bibliotecario elija un
 * nombre y nunca un número.
 */
public class CategoriaController extends CrudController<Categoria> {

    /**
     * Crea el controlador con el DAO de categorías.
     */
    public CategoriaController() {
        super(new CategoriaDAO());
    }

    /**
     * Crea una categoría nueva.
     *
     * @param nombre nombre escrito en el formulario
     * @return {@code true} si quedó guardada
     * @throws IllegalArgumentException si el nombre viene vacío
     */
    public boolean crear(String nombre) {
        String nombreLimpio = Validaciones.requerido(nombre, "nombre");
        return getDao().crear(new Categoria(0, nombreLimpio));
    }

    /**
     * Cambia el nombre de una categoría existente.
     *
     * @param id     identificador de la categoría seleccionada
     * @param nombre nombre nuevo
     * @return {@code true} si se actualizó; {@code false} si la categoría ya no existe
     * @throws IllegalArgumentException si no hay selección o el nombre viene vacío
     */
    public boolean actualizar(int id, String nombre) {
        Validaciones.identificador(id, "una categoría de la tabla");
        String nombreLimpio = Validaciones.requerido(nombre, "nombre");
        return getDao().actualizar(new Categoria(id, nombreLimpio));
    }
}
