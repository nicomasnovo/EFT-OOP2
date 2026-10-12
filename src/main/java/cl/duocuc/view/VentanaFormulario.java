package cl.duocuc.view;

import cl.duocuc.exception.PersistenciaException;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Ventana aparte donde se llenan los formularios de los módulos.
 * <p>
 * Los módulos de {@link EstudianteView estudiantes}, {@link LibroView libros} y
 * {@link PrestamoView préstamos} ya no muestran el formulario sobre la tabla: al
 * presionar «Agregar» o «Modificar» abren esta ventana, que es <b>modal</b>, es decir
 * que bloquea la ventana principal hasta que el usuario guarda o cancela. Así la
 * pantalla del módulo queda solo con el buscador, los botones y la tabla.
 * <p>
 * La ventana no sabe qué se está guardando. Recibe los campos con
 * {@link #agregarCampo(String, Component)} y la acción del botón de guardar al
 * mostrarse. Con eso se encarga de lo que es igual en todos los casos: ordenar las
 * filas, mostrar los botones y decidir si la ventana se cierra o se queda abierta
 * cuando el dato escrito está mal, de modo que el usuario corrija sin volver a
 * escribir todo.
 * <p>
 * <b>Concurrencia.</b> Hay dos formas de mostrarla, según lo que haga el botón de
 * guardar:
 * <ul>
 *   <li>{@link #mostrar(BooleanSupplier)}, cuando la acción solo revisa lo escrito y
 *       no toca la base de datos. Corre en el hilo de Swing, que es donde viven los
 *       campos.</li>
 *   <li>{@link #mostrarYGuardar(Supplier, Predicate)}, cuando hay que escribir en la
 *       base de datos. Los datos se recogen de los campos en el hilo de Swing y
 *       recién después se guardan en un {@link SwingWorker}, con la ventana todavía
 *       abierta y sus botones bloqueados. Si la base de datos rechaza la operación, la
 *       ventana sigue ahí con lo que el usuario había escrito; si la acepta, se cierra
 *       sola.</li>
 * </ul>
 */
public class VentanaFormulario extends JDialog {

    /** Ancho mínimo de la ventana, para que los campos no queden apretados. */
    private static final int ANCHO_MINIMO = 420;

    private final JPanel campos = PanelModulo.crearFormulario();
    private final GridBagConstraints restricciones = PanelModulo.crearRestricciones();
    private final JPanel avisos = new JPanel();
    private final JButton botonAceptar;
    private final JButton botonCancelar;
    private final JLabel etiquetaEstado = new JLabel(" ");

    private int filas;

    /** Qué hace el botón de guardar; lo define el método con que se muestra la ventana. */
    private Runnable alAceptar = () -> { };

    private Boolean resultado;

    /**
     * Crea la ventana del formulario, todavía sin campos, encima de la ventana donde
     * vive el módulo que la abre.
     *
     * @param modulo        módulo que abre el formulario
     * @param titulo        título de la ventana, por ejemplo «Nuevo estudiante»
     * @param textoAceptar  texto del botón que guarda
     * @param colorAceptar  color del botón que guarda, uno de los de {@link PanelModulo}
     */
    protected VentanaFormulario(PanelModulo modulo, String titulo, String textoAceptar,
                                Color colorAceptar) {
        super(SwingUtilities.getWindowAncestor(modulo), titulo,
                ModalityType.APPLICATION_MODAL);

        botonAceptar = PanelModulo.crearBoton(textoAceptar, colorAceptar);
        botonAceptar.addActionListener(e -> alAceptar.run());

        botonCancelar = PanelModulo.crearBoton("Cancelar", PanelModulo.COLOR_SECUNDARIO);
        botonCancelar.addActionListener(e -> dispose());

        avisos.setLayout(new BorderLayout());

        etiquetaEstado.setFont(etiquetaEstado.getFont().deriveFont(Font.ITALIC, 12f));
        etiquetaEstado.setForeground(new Color(90, 90, 90));

        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        contenido.add(encabezado(titulo), BorderLayout.NORTH);
        contenido.add(campos, BorderLayout.CENTER);
        contenido.add(pie(botonAceptar, botonCancelar), BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(contenido);
        getRootPane().setDefaultButton(botonAceptar);
    }

    /**
     * Agrega una fila etiqueta + campo al formulario, debajo de las anteriores.
     *
     * @param etiqueta texto que describe el campo
     * @param campo    componente de entrada: un {@code JTextField}, un {@code JComboBox}, etc.
     */
    protected void agregarCampo(String etiqueta, Component campo) {
        PanelModulo.agregarFila(campos, restricciones, filas++, etiqueta, campo);
    }

    /**
     * Agrega bajo los campos una nota aclaratoria, para explicar algo de la operación
     * sin tener que mostrarla después en otro mensaje.
     *
     * @param texto texto de la nota
     */
    protected void agregarNota(String texto) {
        JLabel nota = new JLabel(texto);
        nota.setFont(nota.getFont().deriveFont(Font.ITALIC, 12f));
        nota.setForeground(new Color(90, 90, 90));

        avisos.removeAll();
        avisos.add(nota, BorderLayout.WEST);
    }

    /**
     * Muestra la ventana para una acción que no toca la base de datos y no devuelve el
     * control hasta que el usuario acepta o cancela.
     * <p>
     * La acción se ejecuta en el hilo de Swing cada vez que se presiona el botón de
     * guardar. Si termina sin problemas, la ventana se cierra y su resultado es el que
     * se devuelve; si rechaza un dato, se avisa dentro de la misma ventana y queda
     * abierta con lo que el usuario había escrito.
     *
     * @param accion revisión de lo escrito, que devuelve si se puede continuar
     * @return lo que devolvió la acción, o {@code null} si el usuario canceló
     */
    protected Boolean mostrar(BooleanSupplier accion) {
        alAceptar = () -> aceptarEnPantalla(accion);
        return abrir();
    }

    /**
     * Muestra la ventana para una operación que escribe en la base de datos y no
     * devuelve el control hasta que el usuario guarda o cancela.
     * <p>
     * Al presionar el botón de guardar se ejecuta primero {@code recolectar}, en el
     * hilo de Swing, que es el que puede leer los campos y validarlos; si algún dato
     * está mal, se avisa y la ventana queda abierta. Con los datos ya reunidos se
     * ejecuta {@code guardar} en un {@link SwingWorker}, de modo que el JDBC no corra
     * en el hilo de la interfaz. La ventana se cierra recién cuando la base de datos
     * confirma la operación.
     *
     * @param recolectar lee los campos y devuelve los datos ya validados
     * @param guardar    guarda esos datos y devuelve si la operación se realizó
     * @param <T>        tipo del objeto que reúne los datos del formulario
     * @return lo que devolvió {@code guardar}, o {@code null} si el usuario canceló
     */
    protected <T> Boolean mostrarYGuardar(Supplier<T> recolectar, Predicate<T> guardar) {
        alAceptar = () -> aceptarGuardando(recolectar, guardar);
        return abrir();
    }

    /**
     * Termina de armar la ventana y la muestra, dejándola del tamaño de sus campos y
     * centrada sobre la ventana que la abrió.
     *
     * @return el resultado que haya quedado al cerrarse
     */
    private Boolean abrir() {
        pack();
        setMinimumSize(new Dimension(Math.max(ANCHO_MINIMO, getWidth()), getHeight()));
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return resultado;
    }

    /**
     * Ejecuta en el hilo de Swing la acción de una ventana que no toca la base de
     * datos y decide si se cierra.
     *
     * @param accion acción que revisa lo escrito
     */
    private void aceptarEnPantalla(BooleanSupplier accion) {
        try {
            resultado = accion.getAsBoolean();
            dispose();
        } catch (IllegalArgumentException e) {
            avisar(e.getMessage(), JOptionPane.WARNING_MESSAGE);
        } catch (PersistenciaException e) {
            avisar(e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Reúne los datos del formulario y los guarda en segundo plano.
     * <p>
     * Mientras la escritura corre, los botones quedan bloqueados y la ventana no se
     * puede cerrar con la cruz: si se cerrara, el módulo creería que el usuario
     * canceló mientras la operación sí se está guardando.
     *
     * @param recolectar lee los campos y devuelve los datos validados
     * @param guardar    guarda esos datos en la base de datos
     * @param <T>        tipo del objeto que reúne los datos del formulario
     */
    private <T> void aceptarGuardando(Supplier<T> recolectar, Predicate<T> guardar) {
        T datos;
        try {
            datos = recolectar.get();
        } catch (IllegalArgumentException e) {
            avisar(e.getMessage(), JOptionPane.WARNING_MESSAGE);
            return;
        }

        guardando(true);

        new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() {
                return guardar.test(datos);
            }

            @Override
            protected void done() {
                guardando(false);
                try {
                    resultado = get();
                    dispose();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    avisar("La operación quedó interrumpida.", JOptionPane.ERROR_MESSAGE);

                } catch (ExecutionException e) {
                    avisarFalla(e.getCause());
                }
            }
        }.execute();
    }

    /**
     * Bloquea o devuelve los controles de la ventana según si hay una escritura en
     * curso.
     *
     * @param enCurso {@code true} mientras la base de datos está guardando
     */
    private void guardando(boolean enCurso) {
        botonAceptar.setEnabled(!enCurso);
        botonCancelar.setEnabled(!enCurso);
        etiquetaEstado.setText(enCurso ? "Guardando en la base de datos..." : " ");
        setDefaultCloseOperation(enCurso ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
    }

    /**
     * Muestra dentro de la ventana el problema que devolvió la escritura, separando un
     * dato rechazado de un fallo de la base de datos.
     *
     * @param causa excepción original que lanzó la tarea de segundo plano
     */
    private void avisarFalla(Throwable causa) {
        if (causa instanceof IllegalArgumentException) {
            avisar(causa.getMessage(), JOptionPane.WARNING_MESSAGE);
        } else if (causa instanceof PersistenciaException) {
            avisar(causa.getMessage(), JOptionPane.ERROR_MESSAGE);
        } else {
            avisar("Ocurrió un problema inesperado: "
                    + (causa == null ? "sin detalle" : causa.getMessage()),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Muestra un mensaje sobre la ventana del formulario, que sigue abierta.
     *
     * @param mensaje texto que se muestra
     * @param tipo    tipo de mensaje de {@link JOptionPane}
     */
    private void avisar(String mensaje, int tipo) {
        JOptionPane.showMessageDialog(this, mensaje, getTitle(), tipo);
    }

    /**
     * Arma el encabezado de la ventana, con el mismo estilo del título de los módulos.
     *
     * @param titulo título de la ventana
     * @return el panel del encabezado
     */
    private JPanel encabezado(String titulo) {
        JLabel etiqueta = new JLabel(titulo);
        etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD, 16f));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(etiqueta, BorderLayout.WEST);
        return panel;
    }

    /**
     * Arma la parte de abajo de la ventana: las notas, los botones y la línea donde se
     * avisa que la operación se está guardando.
     *
     * @param botones botones de la ventana, en el orden en que se quieren ver
     * @return el panel del pie
     */
    private JPanel pie(JButton... botones) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(avisos, BorderLayout.NORTH);
        panel.add(PanelModulo.crearBarraDeBotones(botones), BorderLayout.WEST);
        panel.add(etiquetaEstado, BorderLayout.SOUTH);
        return panel;
    }
}
