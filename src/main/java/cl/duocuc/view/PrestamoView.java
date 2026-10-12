package cl.duocuc.view;

import cl.duocuc.concurrencia.Tareas;
import cl.duocuc.controllers.EstudianteController;
import cl.duocuc.controllers.LibroController;
import cl.duocuc.controllers.PrestamoController;
import cl.duocuc.model.Estudiante;
import cl.duocuc.model.Libro;
import cl.duocuc.model.Prestamo;
import cl.duocuc.model.ResultadoPrestamo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Módulo de préstamos y devoluciones.
 * <p>
 * El panel muestra los préstamos pendientes y los botones de la operación. El
 * estudiante y el libro del nuevo préstamo se eligen en una {@link VentanaFormulario}
 * aparte, que se abre con «Registrar préstamo»; la devolución no necesita formulario,
 * porque el préstamo se elige en la tabla.
 * <p>
 * <b>Concurrencia.</b> Es el módulo donde se ven las dos formas de usar hilos que tiene
 * la aplicación, cada una para lo que sirve:
 * <ul>
 *   <li>Un {@link SwingWorker} saca del hilo de la interfaz cada operación —el listado,
 *       el préstamo, la devolución—, de modo que la ventana no se congele mientras
 *       MySQL trabaja. Lo administra el {@link PanelModulo} con
 *       {@link #ejecutarEnSegundoPlano(String, java.util.function.Supplier,
 *       java.util.function.Consumer)}, que además deshabilita los botones mientras la
 *       tarea dura.</li>
 *   <li>El pool de {@link Tareas} lanza <b>en paralelo</b> las dos consultas que llenan
 *       los combos del formulario, los estudiantes y los libros disponibles. Son
 *       independientes entre sí, así que no hay motivo para esperar una y después la
 *       otra: se envían las dos y se recogen cuando las dos respondieron.</li>
 * </ul>
 * El préstamo y la devolución, además, son transacciones del
 * {@code PrestamoDAO}: la verificación y la escritura del stock ocurren juntas, con las
 * filas bloqueadas, así que lo que la vista recibe ya es el resultado definitivo.
 */
public class PrestamoView extends PanelModulo {

    private final PrestamoController prestamoController;
    private final EstudianteController estudianteController;
    private final LibroController libroController;

    private final JButton botonPrestar = crearBoton("Registrar préstamo", COLOR_CREAR);
    private final JButton botonDevolver = crearBoton("Registrar devolución", COLOR_MODIFICAR);
    private final JButton botonRefrescar = crearBoton("Actualizar", COLOR_SECUNDARIO);

    private final DefaultTableModel modelo = crearModeloTabla(
            "ID", "Estudiante", "Libro", "Fecha préstamo", "Fecha devolución", "Situación");
    private final JTable tabla = crearTabla(modelo);

    /**
     * Crea el módulo de préstamos.
     *
     * @param prestamoController   controlador de préstamos y devoluciones
     * @param estudianteController controlador de estudiantes, que llena el combo de la
     *                             ventana del formulario
     * @param libroController      controlador de libros, que entrega los disponibles
     */
    public PrestamoView(PrestamoController prestamoController,
                        EstudianteController estudianteController,
                        LibroController libroController) {
        super("Préstamos y devoluciones");
        this.prestamoController = prestamoController;
        this.estudianteController = estudianteController;
        this.libroController = libroController;

        botonPrestar.addActionListener(e -> ejecutarConAvisos(this::prepararPrestamo));
        botonDevolver.addActionListener(e -> ejecutarConAvisos(this::registrarDevolucion));
        botonRefrescar.addActionListener(e -> recargar());

        JPanel superior = new JPanel();
        superior.setLayout(new BoxLayout(superior, BoxLayout.Y_AXIS));
        superior.add(crearBarraDeAcciones(botonPrestar, botonDevolver, botonRefrescar));

        armarContenido(superior, new JScrollPane(tabla));
    }

    /**
     * Lee en paralelo lo que necesita el formulario y después lo abre.
     * <p>
     * Las dos consultas se envían al pool de {@link Tareas} y se esperan recién cuando
     * las dos están en camino, de modo que el tiempo total sea el de la más lenta y no
     * la suma de ambas. Todo esto ocurre dentro de la tarea de segundo plano; la ventana
     * se arma después, en el hilo de la interfaz, con las listas ya leídas.
     */
    private void prepararPrestamo() {
        ejecutarEnSegundoPlano("Cargando estudiantes y libros disponibles...",
                this::leerDatosEnParalelo,
                this::abrirFormularioPrestamo);
    }

    /**
     * Lanza las dos consultas del formulario al mismo tiempo y espera sus resultados.
     * <p>
     * Corre en el hilo del {@link SwingWorker}, nunca en el de Swing, porque
     * {@link Tareas#esperar(Future)} bloquea hasta que la consulta responde.
     *
     * @return los estudiantes y los libros disponibles
     */
    private DatosDelPrestamo leerDatosEnParalelo() {
        Future<List<Estudiante>> estudiantes = Tareas.enviar(estudianteController::listar);
        Future<List<Libro>> disponibles = Tareas.enviar(libroController::listarDisponibles);

        return new DatosDelPrestamo(Tareas.esperar(estudiantes), Tareas.esperar(disponibles));
    }

    /**
     * Abre la ventana donde se elige el estudiante y el libro, y registra el préstamo
     * con lo que se haya elegido.
     *
     * @param datos listas ya leídas con que se llenan los combos
     */
    private void abrirFormularioPrestamo(DatosDelPrestamo datos) {
        Formulario formulario = new Formulario(datos);

        if (!Boolean.TRUE.equals(formulario.mostrar(formulario::validar))) {
            return;
        }
        registrarPrestamo(formulario.estudiante(), formulario.libro());
    }

    /**
     * Registra el préstamo del libro elegido al estudiante elegido, en segundo plano.
     * <p>
     * La verificación del stock, la comprobación de que el estudiante no tenga ya ese
     * libro, la inserción del préstamo y el descuento del ejemplar ocurren dentro de una
     * sola transacción, que corre en el hilo de segundo plano. Acá solo se traduce a un
     * mensaje el {@link ResultadoPrestamo} que esa transacción devolvió.
     *
     * @param estudiante estudiante que recibe el libro
     * @param libro      libro que se presta
     */
    private void registrarPrestamo(Estudiante estudiante, Libro libro) {
        ejecutarEnSegundoPlano("Registrando el préstamo en segundo plano...",
                () -> prestamoController.registrarPrestamo(estudiante.getId(), libro.getId()),
                resultado -> {
                    avisarResultado(resultado, estudiante, libro);
                    recargar();
                });
    }

    /**
     * Muestra el mensaje que corresponde a cómo terminó el intento de préstamo.
     *
     * @param resultado  lo que devolvió la transacción
     * @param estudiante estudiante que iba a recibir el libro
     * @param libro      libro que se intentó prestar
     */
    private void avisarResultado(ResultadoPrestamo resultado, Estudiante estudiante,
                                 Libro libro) {
        switch (resultado) {
            case REGISTRADO -> mostrarInfo("Préstamo registrado. \"" + libro.getTitulo()
                    + "\" debe devolverse en " + Prestamo.DIAS_DE_PRESTAMO + " días.");

            case SIN_EJEMPLARES -> mostrarAviso("El libro \"" + libro.getTitulo()
                    + "\" se quedó sin ejemplares disponibles. No se registró el préstamo.");

            case YA_LO_TIENE_PRESTADO -> mostrarAviso(estudiante.getNombre()
                    + " ya tiene \"" + libro.getTitulo() + "\" en préstamo y todavía no lo "
                    + "devuelve. No se registró un segundo préstamo del mismo libro.");
        }
    }

    /**
     * Registra la devolución del préstamo seleccionado, en segundo plano.
     * <p>
     * El controlador marca el préstamo como devuelto y suma el ejemplar al stock en la
     * misma transacción. Si ese préstamo ya estaba devuelto, la operación no se repite.
     */
    private void registrarDevolucion() {
        int id = idSeleccionado(tabla);
        if (id <= 0) {
            mostrarAviso("Debe seleccionar un préstamo pendiente de la tabla.");
            return;
        }

        String titulo = String.valueOf(tabla.getValueAt(tabla.getSelectedRow(), 2));
        if (!confirmar("¿Registrar la devolución de \"" + titulo + "\"?")) {
            return;
        }

        ejecutarEnSegundoPlano("Registrando la devolución en segundo plano...",
                () -> prestamoController.registrarDevolucion(id),
                registrada -> {
                    if (registrada) {
                        mostrarInfo("Devolución registrada. El ejemplar volvió al stock.");
                    } else {
                        mostrarAviso("Ese préstamo ya estaba devuelto.");
                    }
                    recargar();
                });
    }

    /**
     * Vuelve a leer desde la base de datos los préstamos pendientes.
     */
    @Override
    public void recargar() {
        ejecutarEnSegundoPlano("Cargando los préstamos pendientes...",
                prestamoController::listarPendientes,
                this::mostrarEnTabla);
    }

    /**
     * Llena la tabla con los préstamos que todavía no se han devuelto, marcando los
     * que están fuera de plazo.
     *
     * @param pendientes préstamos leídos de la base de datos
     */
    private void mostrarEnTabla(List<Prestamo> pendientes) {
        modelo.setRowCount(0);
        for (Prestamo prestamo : pendientes) {
            modelo.addRow(new Object[]{
                    prestamo.getId(),
                    prestamo.getNombreEstudiante(),
                    prestamo.getTituloLibro(),
                    prestamo.getFechaPrestamo(),
                    prestamo.getFechaDevolucion(),
                    prestamo.estaAtrasado() ? "Atrasado" : "En plazo"});
        }
    }

    /**
     * Lo que hay que leer antes de abrir el formulario de un préstamo: los estudiantes
     * registrados y los libros que en ese momento tienen ejemplares.
     * <p>
     * Existe para que las dos consultas paralelas vuelvan juntas en un solo resultado,
     * que es lo que el {@code SwingWorker} entrega de vuelta al hilo de la interfaz.
     *
     * @param estudiantes estudiantes a los que se les puede prestar
     * @param disponibles libros con stock mayor que cero
     */
    private record DatosDelPrestamo(List<Estudiante> estudiantes, List<Libro> disponibles) {
    }

    /**
     * Ventana donde se elige el estudiante y el libro del préstamo.
     * <p>
     * No consulta la base de datos: recibe las dos listas ya leídas, así que muestra los
     * estudiantes registrados y los libros que tenían ejemplares cuando se abrió. La
     * ventana solo reúne los datos; el registro lo hace el módulo en segundo plano una
     * vez que se cierra, y es la transacción la que confirma que el libro siga
     * disponible.
     */
    private final class Formulario extends VentanaFormulario {

        private final JComboBox<Estudiante> comboEstudiantes = new JComboBox<>();
        private final JComboBox<Libro> comboLibros = new JComboBox<>();

        /**
         * Arma la ventana con los dos combos ya cargados.
         *
         * @param datos listas con que se llenan los combos
         */
        private Formulario(DatosDelPrestamo datos) {
            super(PrestamoView.this, "Nuevo préstamo", "Registrar préstamo", COLOR_CREAR);

            agregarCampo("Estudiante:", comboEstudiantes);
            agregarCampo("Libro disponible:", comboLibros);
            agregarNota("El libro se debe devolver en " + Prestamo.DIAS_DE_PRESTAMO
                    + " días.");

            for (Estudiante estudiante : datos.estudiantes()) {
                comboEstudiantes.addItem(estudiante);
            }
            for (Libro libro : datos.disponibles()) {
                comboLibros.addItem(libro);
            }
        }

        /**
         * Revisa que haya un estudiante y un libro elegidos. Si falta uno, la ventana
         * queda abierta con el aviso correspondiente.
         *
         * @return {@code true} cuando el préstamo se puede registrar
         */
        private boolean validar() {
            if (estudiante() == null) {
                throw new IllegalArgumentException("Debe elegir un estudiante. Si la lista "
                        + "está vacía, registre primero un estudiante.");
            }
            if (libro() == null) {
                throw new IllegalArgumentException(
                        "No hay libros con ejemplares disponibles para prestar.");
            }
            return true;
        }

        /**
         * Devuelve el estudiante elegido.
         *
         * @return el estudiante del combo, o {@code null} si no hay ninguno
         */
        private Estudiante estudiante() {
            return (Estudiante) comboEstudiantes.getSelectedItem();
        }

        /**
         * Devuelve el libro elegido.
         *
         * @return el libro del combo, o {@code null} si no hay ninguno
         */
        private Libro libro() {
            return (Libro) comboLibros.getSelectedItem();
        }
    }
}
