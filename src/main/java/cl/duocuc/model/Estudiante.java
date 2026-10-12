package cl.duocuc.model;

/**
 * Persona que pide libros prestados. Corresponde a una fila de la tabla
 * {@code estudiantes} y agrega a {@link Persona} el curso al que pertenece.
 * <p>
 * Es la entidad que referencian los préstamos mediante
 * {@code prestamos.id_estudiante}, por lo que un estudiante con préstamos
 * registrados no puede eliminarse.
 */
public class Estudiante extends Persona {

    private String curso;

    /**
     * Crea un estudiante con sus datos.
     *
     * @param id     identificador en la tabla {@code estudiantes}; {@code 0} si es nuevo
     * @param nombre nombre completo
     * @param rut    RUT con guion
     * @param curso  curso al que pertenece, por ejemplo {@code "3ro Medio A"}
     * @param correo correo electrónico
     */
    public Estudiante(int id, String nombre, String rut, String curso, String correo) {
        super(id, nombre, rut, correo);
        this.curso = curso;
    }

    public String getCurso() {
        return curso;
    }

    public void setCurso(String curso) {
        this.curso = curso;
    }

    /**
     * Describe al estudiante por su nombre y su curso, que es como se elige en el
     * módulo de préstamos.
     *
     * @return por ejemplo {@code "Carlos Ruiz - 3ro Medio A"}
     */
    @Override
    public String getDescripcion() {
        return getNombre() + " - " + curso;
    }
}
