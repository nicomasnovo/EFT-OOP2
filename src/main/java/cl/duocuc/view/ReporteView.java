package cl.duocuc.view;

import cl.duocuc.controllers.EstudianteController;
import cl.duocuc.controllers.ReporteController;
import cl.duocuc.model.Estudiante;
import cl.duocuc.model.Reporte;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Módulo de reportes. Muestra en un {@code JTable} los tres informes que pide la
 * biblioteca: los libros más prestados, el historial de un estudiante y los libros que
 * están prestados en este momento.
 * <p>
 * El mismo módulo atiende los dos roles. El bibliotecario elige cualquiera de los tres
 * reportes y, para el historial, cualquier estudiante. Un estudiante que entra con su
 * cuenta solo recibe su propio historial: no se le ofrece el selector de reportes ni el
 * de estudiantes, porque el módulo se construye con su ficha fija.
 * <p>
 * <b>Concurrencia.</b> Los reportes son las consultas más pesadas de la aplicación
 * —llevan {@code JOIN}, {@code GROUP BY} y {@code COUNT}—, así que es donde más importa
 * que no corran en el hilo de la interfaz. El módulo decide primero qué reporte pide,
 * leyendo los selectores en el hilo de Swing, y recién después manda esa consulta a
 * segundo plano.
 */
public class ReporteView extends PanelModulo {

    /** Opciones del selector de reportes, en el orden en que se muestran. */
    private static final String LIBROS_MAS_PRESTADOS = "Libros más prestados";
    private static final String HISTORIAL_ESTUDIANTE = "Historial de un estudiante";
    private static final String LIBROS_PRESTADOS = "Libros actualmente prestados";

    private final ReporteController reporteController;
    private final EstudianteController estudianteController;

    /** Ficha del estudiante que inició sesión, o {@code null} si es el bibliotecario. */
    private final Estudiante fichaDeLaSesion;

    private final JComboBox<String> comboReportes = new JComboBox<>();
    private final JComboBox<Estudiante> comboEstudiantes = new JComboBox<>();

    private final DefaultTableModel modelo = crearModeloTabla("Reporte");
    private final JTable tabla = crearTabla(modelo);

    /**
     * Crea el módulo de reportes.
     *
     * @param reporteController    controlador de reportes
     * @param estudianteController controlador de estudiantes, que llena el combo del
     *                             historial
     * @param fichaDeLaSesion      ficha del estudiante que inició sesión, o
     *                             {@code null} si quien entró es el bibliotecario
     */
    public ReporteView(ReporteController reporteController,
                       EstudianteController estudianteController,
                       Estudiante fichaDeLaSesion) {
        super(fichaDeLaSesion == null ? "Reportes de la biblioteca" : "Mis préstamos");
        this.reporteController = reporteController;
        this.estudianteController = estudianteController;
        this.fichaDeLaSesion = fichaDeLaSesion;

        JPanel superior = new JPanel();
        superior.setLayout(new BoxLayout(superior, BoxLayout.Y_AXIS));
        superior.add(crearSelectores());
        superior.add(crearAcciones());

        armarContenido(superior, new JScrollPane(tabla));
    }

    /**
     * Arma los selectores del módulo. El del tipo de reporte y el de estudiantes solo
     * se agregan cuando quien entró es el bibliotecario.
     *
     * @return el panel con los selectores
     */
    private JPanel crearSelectores() {
        JPanel formulario = crearFormulario();
        GridBagConstraints restricciones = crearRestricciones();

        if (esBibliotecario()) {
            comboReportes.addItem(LIBROS_MAS_PRESTADOS);
            comboReportes.addItem(HISTORIAL_ESTUDIANTE);
            comboReportes.addItem(LIBROS_PRESTADOS);
            comboReportes.addActionListener(e -> actualizarSelectorDeEstudiante());

            agregarFila(formulario, restricciones, 0, "Reporte:", comboReportes);
            agregarFila(formulario, restricciones, 1, "Estudiante:", comboEstudiantes);
            actualizarSelectorDeEstudiante();
        }
        return formulario;
    }

    /**
     * Arma la barra con el botón que genera el reporte.
     *
     * @return el panel con el botón
     */
    private JPanel crearAcciones() {
        JButton botonGenerar = crearBoton("Generar reporte", COLOR_MODIFICAR);
        botonGenerar.addActionListener(e -> ejecutarConAvisos(this::generar));
        return crearBarraDeAcciones(botonGenerar);
    }

    /**
     * Muestra el selector de estudiantes solo cuando el reporte elegido lo necesita.
     */
    private void actualizarSelectorDeEstudiante() {
        comboEstudiantes.setEnabled(HISTORIAL_ESTUDIANTE.equals(comboReportes.getSelectedItem()));
    }

    /**
     * Averigua qué reporte se pidió, lo genera en segundo plano y lo vuelca en la tabla.
     */
    private void generar() {
        Supplier<Reporte> consulta = elegirConsulta();

        ejecutarEnSegundoPlano("Generando el reporte...", consulta, this::mostrarReporte);
    }

    /**
     * Decide qué consulta hay que ejecutar según lo que esté elegido en los selectores.
     * <p>
     * Se ejecuta en el hilo de la interfaz, porque lee los combos, pero no consulta
     * nada: devuelve la consulta <b>sin ejecutar</b>, con el identificador del
     * estudiante ya resuelto, para que la corra el hilo de segundo plano.
     *
     * @return la consulta del reporte pedido, lista para ejecutarse
     * @throws IllegalArgumentException si falta elegir el estudiante del historial
     */
    private Supplier<Reporte> elegirConsulta() {
        if (!esBibliotecario()) {
            int idDeLaSesion = fichaDeLaSesion.getId();
            return () -> reporteController.historialEstudiante(idDeLaSesion);
        }

        Object elegido = comboReportes.getSelectedItem();

        if (HISTORIAL_ESTUDIANTE.equals(elegido)) {
            Estudiante estudiante = (Estudiante) comboEstudiantes.getSelectedItem();
            if (estudiante == null) {
                throw new IllegalArgumentException(
                        "Debe elegir un estudiante para ver su historial.");
            }
            int idElegido = estudiante.getId();
            return () -> reporteController.historialEstudiante(idElegido);
        }

        if (LIBROS_PRESTADOS.equals(elegido)) {
            return reporteController::librosActualmentePrestados;
        }

        return reporteController::librosMasPrestados;
    }

    /**
     * Vuelca en la tabla el reporte que trajo la consulta.
     *
     * @param reporte reporte generado
     */
    private void mostrarReporte(Reporte reporte) {
        modelo.setDataVector(reporte.comoMatriz(), reporte.columnas());

        if (reporte.estaVacio()) {
            mostrarInfo("El reporte no tiene datos todavía.");
        }
    }

    /**
     * Indica si el módulo se está mostrando al bibliotecario.
     *
     * @return {@code true} si la sesión no tiene ficha de estudiante
     */
    private boolean esBibliotecario() {
        return fichaDeLaSesion == null;
    }

    /**
     * Vuelve a leer la lista de estudiantes del selector. La tabla no se regenera
     * sola: el reporte se calcula cuando el usuario lo pide, para no consultar
     * agrupaciones cada vez que se cambia de módulo.
     */
    @Override
    public void recargar() {
        if (!esBibliotecario()) {
            return;
        }

        Estudiante elegido = (Estudiante) comboEstudiantes.getSelectedItem();

        ejecutarEnSegundoPlano("Cargando los estudiantes...",
                estudianteController::listar,
                estudiantes -> llenarSelector(estudiantes, elegido));
    }

    /**
     * Llena el selector de estudiantes y, si el que estaba elegido sigue existiendo, lo
     * vuelve a dejar elegido.
     *
     * @param estudiantes estudiantes leídos de la base de datos
     * @param elegido     estudiante que estaba seleccionado, o {@code null} si no había
     */
    private void llenarSelector(List<Estudiante> estudiantes, Estudiante elegido) {
        comboEstudiantes.removeAllItems();

        for (Estudiante estudiante : estudiantes) {
            comboEstudiantes.addItem(estudiante);
            if (elegido != null && elegido.getId() == estudiante.getId()) {
                comboEstudiantes.setSelectedItem(estudiante);
            }
        }
    }
}
