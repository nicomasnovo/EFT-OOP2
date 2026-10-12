package cl.duocuc.controllers;

import cl.duocuc.dao.EstudianteDAO;
import cl.duocuc.model.Estudiante;

import java.util.List;

/**
 * Controlador del módulo de estudiantes. Hereda de {@link CrudController} el
 * listado, la búsqueda por identificador y la eliminación, y agrega la creación, la
 * modificación y la búsqueda por texto con sus validaciones.
 * <p>
 * Igual que en el controlador de libros, la validación y la escritura están separadas
 * porque ocurren en hilos distintos: {@link #validar(String, String, String, String)}
 * se llama desde el hilo de Swing, que es el que puede leer los campos del formulario,
 * y {@link #crear(Estudiante)} y {@link #actualizar(int, Estudiante)} desde el hilo de
 * segundo plano que hace el JDBC.
 */
public class EstudianteController extends CrudController<Estudiante> {

    /**
     * Mismo DAO que recibe la clase base, guardado aparte para usar la búsqueda por
     * texto, que es propia de los estudiantes.
     */
    private final EstudianteDAO estudianteDAO;

    /**
     * Crea el controlador con el DAO de estudiantes.
     */
    public EstudianteController() {
        this(new EstudianteDAO());
    }

    /**
     * Crea el controlador con un DAO ya construido, de modo que la clase base y las
     * consultas propias usen la misma instancia.
     *
     * @param estudianteDAO acceso a datos de los estudiantes
     */
    private EstudianteController(EstudianteDAO estudianteDAO) {
        super(estudianteDAO);
        this.estudianteDAO = estudianteDAO;
    }

    /**
     * Revisa los datos escritos en el formulario y arma con ellos la ficha que se
     * guardará. No toca la base de datos, así que la vista la llama desde el hilo de
     * la interfaz, antes de mandar la escritura a segundo plano.
     *
     * @param nombre nombre completo
     * @param rut    RUT con guion, que no puede repetirse en la tabla
     * @param curso  curso al que pertenece
     * @param correo correo electrónico
     * @return el estudiante validado, listo para guardarse
     * @throws IllegalArgumentException si falta un campo o el RUT o el correo no
     *                                 tienen un formato válido
     */
    public Estudiante validar(String nombre, String rut, String curso, String correo) {
        return new Estudiante(0,
                Validaciones.requerido(nombre, "nombre"),
                Validaciones.rut(rut),
                Validaciones.requerido(curso, "curso"),
                Validaciones.correo(correo));
    }

    /**
     * Registra un estudiante ya validado.
     *
     * @param estudiante estudiante que devolvió
     *                   {@link #validar(String, String, String, String)}
     * @return {@code true} si quedó guardado
     */
    public boolean crear(Estudiante estudiante) {
        return getDao().crear(estudiante);
    }

    /**
     * Guarda los datos nuevos de un estudiante existente.
     *
     * @param id         identificador del estudiante seleccionado
     * @param estudiante estudiante validado con los datos nuevos
     * @return {@code true} si se actualizó; {@code false} si el estudiante ya no existe
     * @throws IllegalArgumentException si no hay un estudiante seleccionado
     */
    public boolean actualizar(int id, Estudiante estudiante) {
        Validaciones.identificador(id, "un estudiante de la tabla");
        estudiante.setId(id);
        return getDao().actualizar(estudiante);
    }

    /**
     * Busca estudiantes por nombre, RUT o curso. Si el texto viene vacío devuelve la
     * lista completa, para que limpiar el buscador equivalga a volver a listar.
     *
     * @param texto texto escrito en el buscador
     * @return los estudiantes que coinciden
     */
    public List<Estudiante> buscar(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        return limpio.isEmpty() ? listar() : estudianteDAO.buscar(limpio);
    }

}
