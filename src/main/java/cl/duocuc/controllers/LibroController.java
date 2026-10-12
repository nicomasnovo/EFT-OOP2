package cl.duocuc.controllers;

import cl.duocuc.dao.LibroDAO;
import cl.duocuc.model.Categoria;
import cl.duocuc.model.Libro;

import java.util.List;

/**
 * Controlador del módulo de libros. Hereda de {@link CrudController} el listado, la
 * búsqueda por identificador y la eliminación, y agrega la creación, la modificación
 * y la búsqueda por texto con sus validaciones.
 * <p>
 * Recibe de la vista la {@link Categoria} que se eligió en el {@code JComboBox} y es
 * él quien saca de ahí el {@code id_categoria} que necesita la tabla: la vista nunca
 * trabaja con esa clave foránea.
 * <p>
 * La validación y la escritura están separadas en dos pasos a propósito, porque
 * ocurren en hilos distintos: {@link #validar(String, String, String, String, String,
 * Categoria)} revisa lo escrito en el formulario y se llama desde el hilo de Swing,
 * que es el único que puede leer los campos; {@link #crear(Libro)} y
 * {@link #actualizar(int, Libro, int)} reciben el libro ya validado y se llaman desde
 * un hilo de segundo plano, que es donde debe ocurrir el JDBC.
 */
public class LibroController extends CrudController<Libro> {

    /**
     * Mismo DAO que recibe la clase base, guardado aparte para usar las consultas
     * propias de los libros: la búsqueda por texto y el listado de disponibles.
     */
    private final LibroDAO libroDAO;

    /**
     * Crea el controlador con el DAO de libros.
     */
    public LibroController() {
        this(new LibroDAO());
    }

    /**
     * Crea el controlador con un DAO ya construido, de modo que la clase base y las
     * consultas propias usen la misma instancia.
     *
     * @param libroDAO acceso a datos de los libros
     */
    private LibroController(LibroDAO libroDAO) {
        super(libroDAO);
        this.libroDAO = libroDAO;
    }

    /**
     * Revisa los datos escritos en el formulario y arma con ellos el libro que se
     * guardará. No toca la base de datos, así que la vista la llama desde el hilo de
     * la interfaz, antes de mandar la escritura a segundo plano.
     *
     * @param titulo    título del libro
     * @param autor     autor del libro
     * @param isbn      ISBN, que no puede repetirse en la tabla
     * @param editorial editorial del libro
     * @param stock     ejemplares disponibles, tal como se escribió en el formulario
     * @param categoria categoría elegida en el {@code JComboBox}
     * @return el libro validado, listo para guardarse
     * @throws IllegalArgumentException si falta un campo, el stock no es un número
     *                                 válido o no se eligió categoría
     */
    public Libro validar(String titulo, String autor, String isbn, String editorial,
                         String stock, Categoria categoria) {
        if (categoria == null) {
            throw new IllegalArgumentException("Debe elegir una categoría para el libro.");
        }

        return new Libro(0,
                Validaciones.requerido(titulo, "título"),
                Validaciones.requerido(autor, "autor"),
                Validaciones.requerido(isbn, "ISBN"),
                Validaciones.requerido(editorial, "editorial"),
                Validaciones.stock(stock),
                categoria.getId());
    }

    /**
     * Agrega al catálogo un libro ya validado.
     *
     * @param libro libro que devolvió
     *              {@link #validar(String, String, String, String, String, Categoria)}
     * @return {@code true} si quedó guardado
     */
    public boolean crear(Libro libro) {
        return getDao().crear(libro);
    }

    /**
     * Guarda los datos nuevos de un libro existente, cuidando el stock.
     * <p>
     * Según lo que el bibliotecario haya cambiado, elige una de las dos
     * actualizaciones del DAO: si el stock quedó igual al que mostraba el formulario,
     * guarda la ficha sin tocar esa columna, de modo que la operación no puede chocar
     * con un préstamo; si el stock cambió, lo escribe con bloqueo optimista y la
     * operación se rechaza cuando entremedio hubo un préstamo o una devolución.
     *
     * @param id            identificador del libro seleccionado
     * @param libro         libro validado con los datos nuevos
     * @param stockOriginal stock que el formulario leyó al abrirse
     * @return {@code true} si se actualizó; {@code false} si el libro ya no existe
     * @throws IllegalArgumentException si no hay un libro seleccionado
     * @throws cl.duocuc.exception.PersistenciaException si el stock cambió mientras se
     *                                 editaba la ficha
     */
    public boolean actualizar(int id, Libro libro, int stockOriginal) {
        Validaciones.identificador(id, "un libro de la tabla");
        libro.setId(id);

        return libro.getStock() == stockOriginal
                ? libroDAO.actualizar(libro)
                : libroDAO.actualizarConStock(libro, stockOriginal);
    }

    /**
     * Busca libros por título, autor o ISBN. Si el texto viene vacío devuelve el
     * catálogo completo, para que limpiar el buscador equivalga a volver a listar.
     *
     * @param texto texto escrito en el buscador
     * @return los libros que coinciden
     */
    public List<Libro> buscar(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        return limpio.isEmpty() ? listar() : libroDAO.buscar(limpio);
    }

    /**
     * Devuelve los libros con ejemplares disponibles, que son los que se pueden
     * prestar.
     *
     * @return los libros con stock mayor que cero
     */
    public List<Libro> listarDisponibles() {
        return libroDAO.listarDisponibles();
    }

}
