package cl.duocuc.model;

/**
 * Libro del catálogo. Corresponde a una fila de la tabla {@code libros}, con sus
 * siete columnas: título, autor, ISBN, editorial, stock y la categoría a la que
 * pertenece.
 * <p>
 * Además del {@code idCategoria} que viaja a MySQL, guarda el
 * {@code nombreCategoria} que el {@code LibroDAO} trae con un {@code JOIN}. Ese
 * nombre no es una columna de la tabla: existe solo para que la interfaz muestre
 * "Ciencia Ficción" en vez de un número.
 * <p>
 * El {@code stock} es la cantidad de ejemplares disponibles para prestar: baja al
 * registrar un préstamo y sube al registrar la devolución.
 */
public class Libro {

    private int id;
    private String titulo;
    private String autor;
    private String isbn;
    private String editorial;
    private int stock;
    private int idCategoria;
    private String nombreCategoria;

    /**
     * Crea un libro con los datos que se guardan en la tabla.
     *
     * @param id          identificador en la tabla {@code libros}; {@code 0} si es nuevo
     * @param titulo      título del libro
     * @param autor       autor del libro
     * @param isbn        ISBN, único en la tabla
     * @param editorial   editorial
     * @param stock       ejemplares disponibles, nunca negativo
     * @param idCategoria identificador de la categoría a la que pertenece
     */
    public Libro(int id, String titulo, String autor, String isbn, String editorial,
                 int stock, int idCategoria) {
        this.id = id;
        this.titulo = titulo;
        this.autor = autor;
        this.isbn = isbn;
        this.editorial = editorial;
        this.stock = stock;
        this.idCategoria = idCategoria;
    }

    /**
     * Indica si queda al menos un ejemplar para prestar.
     *
     * @return {@code true} si el stock es mayor que cero
     */
    public boolean tieneStock() {
        return stock > 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getEditorial() {
        return editorial;
    }

    public void setEditorial(String editorial) {
        this.editorial = editorial;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(int idCategoria) {
        this.idCategoria = idCategoria;
    }

    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public void setNombreCategoria(String nombreCategoria) {
        this.nombreCategoria = nombreCategoria;
    }

    /**
     * Devuelve el título y el stock disponible, que es lo que necesita ver el
     * bibliotecario al elegir un libro para prestar.
     *
     * @return por ejemplo {@code "Dune (5 disponibles)"}
     */
    @Override
    public String toString() {
        return titulo + " (" + stock + " disponibles)";
    }
}
