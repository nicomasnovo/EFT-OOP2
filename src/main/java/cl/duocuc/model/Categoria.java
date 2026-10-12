package cl.duocuc.model;

/**
 * Categoría temática de los libros. Corresponde a una fila de la tabla
 * {@code categorias}, que solo tiene identificador y nombre.
 * <p>
 * Los libros la referencian con {@code libros.id_categoria}. La interfaz nunca
 * pide ese número al usuario: muestra las categorías en un {@code JComboBox} y
 * usa este objeto para traducir entre el nombre visible y el identificador que
 * necesita la base de datos.
 */
public class Categoria {

    private int id;
    private String nombre;

    /**
     * Crea una categoría.
     *
     * @param id     identificador en la tabla {@code categorias}; {@code 0} si es nueva
     * @param nombre nombre de la categoría
     */
    public Categoria(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Devuelve el nombre, de modo que el {@code JComboBox} de categorías lo muestre
     * directamente sin necesitar un renderizador aparte.
     *
     * @return el nombre de la categoría
     */
    @Override
    public String toString() {
        return nombre;
    }
}
