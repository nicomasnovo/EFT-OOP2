package cl.duocuc.model;

import java.time.LocalDate;

/**
 * Préstamo de un libro a un estudiante. Corresponde a una fila de la tabla
 * {@code prestamos}: quién pidió el libro, qué libro, cuándo lo pidió, hasta
 * cuándo lo tiene y si ya lo devolvió.
 * <p>
 * Las fechas se manejan como {@link LocalDate} y el DAO las traduce a
 * {@code java.sql.Date} al hablar con MySQL. La fecha de devolución se calcula al
 * registrar el préstamo como {@code fechaPrestamo.plusDays(}{@value #DIAS_DE_PRESTAMO}{@code )}.
 * <p>
 * El nombre del estudiante y el título del libro no son columnas de la tabla:
 * llegan del {@code JOIN} que hace el {@code PrestamoDAO} para que las tablas de
 * la interfaz y los reportes no muestren solo identificadores.
 */
public class Prestamo {

    /** Días que un estudiante puede conservar un libro antes de devolverlo. */
    public static final int DIAS_DE_PRESTAMO = 7;

    private int id;
    private int idEstudiante;
    private int idLibro;
    private LocalDate fechaPrestamo;
    private LocalDate fechaDevolucion;
    private boolean devuelto;
    private String nombreEstudiante;
    private String tituloLibro;

    /**
     * Crea un préstamo con los datos que se guardan en la tabla.
     *
     * @param id              identificador en la tabla {@code prestamos}; {@code 0} si es nuevo
     * @param idEstudiante    estudiante que recibe el libro
     * @param idLibro         libro prestado
     * @param fechaPrestamo   día en que se entrega el libro
     * @param fechaDevolucion día en que se debe devolver
     * @param devuelto        {@code true} si el libro ya volvió a la biblioteca
     */
    public Prestamo(int id, int idEstudiante, int idLibro, LocalDate fechaPrestamo,
                    LocalDate fechaDevolucion, boolean devuelto) {
        this.id = id;
        this.idEstudiante = idEstudiante;
        this.idLibro = idLibro;
        this.fechaPrestamo = fechaPrestamo;
        this.fechaDevolucion = fechaDevolucion;
        this.devuelto = devuelto;
    }

    /**
     * Crea un préstamo nuevo para hoy, con la fecha de devolución ya calculada.
     *
     * @param idEstudiante estudiante que recibe el libro
     * @param idLibro      libro prestado
     * @return el préstamo listo para guardarse, todavía sin identificador
     */
    public static Prestamo nuevo(int idEstudiante, int idLibro) {
        LocalDate hoy = LocalDate.now();
        return new Prestamo(0, idEstudiante, idLibro, hoy, hoy.plusDays(DIAS_DE_PRESTAMO), false);
    }

    /**
     * Indica si el plazo de devolución ya venció y el libro sigue afuera.
     *
     * @return {@code true} si está atrasado
     */
    public boolean estaAtrasado() {
        return !devuelto && fechaDevolucion != null && fechaDevolucion.isBefore(LocalDate.now());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdEstudiante() {
        return idEstudiante;
    }

    public void setIdEstudiante(int idEstudiante) {
        this.idEstudiante = idEstudiante;
    }

    public int getIdLibro() {
        return idLibro;
    }

    public void setIdLibro(int idLibro) {
        this.idLibro = idLibro;
    }

    public LocalDate getFechaPrestamo() {
        return fechaPrestamo;
    }

    public void setFechaPrestamo(LocalDate fechaPrestamo) {
        this.fechaPrestamo = fechaPrestamo;
    }

    public LocalDate getFechaDevolucion() {
        return fechaDevolucion;
    }

    public void setFechaDevolucion(LocalDate fechaDevolucion) {
        this.fechaDevolucion = fechaDevolucion;
    }

    public boolean isDevuelto() {
        return devuelto;
    }

    public void setDevuelto(boolean devuelto) {
        this.devuelto = devuelto;
    }

    public String getNombreEstudiante() {
        return nombreEstudiante;
    }

    public void setNombreEstudiante(String nombreEstudiante) {
        this.nombreEstudiante = nombreEstudiante;
    }

    public String getTituloLibro() {
        return tituloLibro;
    }

    public void setTituloLibro(String tituloLibro) {
        this.tituloLibro = tituloLibro;
    }
}
