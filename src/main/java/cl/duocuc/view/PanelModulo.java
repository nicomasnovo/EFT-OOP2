package cl.duocuc.view;

import cl.duocuc.exception.PersistenciaException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base de los módulos que se muestran en el área central de la {@link MainView}.
 * Cada módulo es un {@link JPanel} con el mismo título, los mismos márgenes y la
 * misma forma de armar formularios, tablas y botones, de modo que las pantallas se
 * vean iguales sin repetir ese código.
 * <p>
 * Es una clase abstracta y obliga a cada módulo a implementar {@link #recargar()}.
 * Gracias a eso la {@link MainView} puede guardar sus módulos en una sola colección
 * de {@code PanelModulo} y pedirles que se actualicen sin saber si está hablando con
 * el de libros, el de préstamos o el de reportes.
 * <p>
 * También reúne la forma de avisar al usuario: los módulos no construyen mensajes a
 * mano, llaman a {@link #mostrarInfo(String)}, {@link #mostrarAviso(String)} o
 * {@link #mostrarError(String)}, y envuelven sus acciones con
 * {@link #ejecutarConAvisos(Runnable)} para que un dato mal escrito o un fallo de la
 * base de datos se muestren siempre del mismo modo.
 * <p>
 * <b>Concurrencia.</b> Aquí vive la regla que siguen todos los módulos: ninguna
 * consulta ni escritura de la base de datos ocurre en el hilo de Swing. Para eso está
 * {@link #ejecutarEnSegundoPlano(String, Supplier, Consumer)}, que corre la operación
 * en un {@code SwingWorker} y vuelve al hilo de la interfaz solo para mostrar el
 * resultado. Mientras hay tareas en curso, la clase deshabilita los botones de acción
 * del módulo y escribe el aviso correspondiente en su línea de estado, de modo que la
 * ventana nunca se congela y la misma operación no se puede disparar dos veces.
 * <p>
 * El reparto de responsabilidades entre los dos ayudantes es el que conviene recordar:
 * {@link #ejecutarConAvisos(Runnable)} es para trabajo que solo toca la pantalla y
 * {@link #ejecutarEnSegundoPlano(String, Supplier, Consumer)} para todo lo que pase
 * por un controlador.
 * <p>
 * Los módulos que dan de alta o modifican registros no muestran el formulario en el
 * panel: lo abren en una {@link VentanaFormulario}, que se arma con estos mismos
 * ayudantes para que los campos se vean igual dentro y fuera de la ventana principal.
 */
public abstract class PanelModulo extends JPanel {

    /** Color del botón que crea registros. */
    protected static final Color COLOR_CREAR = new Color(40, 167, 69);

    /** Color del botón que modifica o confirma registros. */
    protected static final Color COLOR_MODIFICAR = new Color(52, 120, 220);

    /** Color del botón que elimina registros. */
    protected static final Color COLOR_ELIMINAR = new Color(220, 53, 69);

    /** Color de los botones que no escriben en la base de datos: limpiar, buscar, refrescar. */
    protected static final Color COLOR_SECUNDARIO = new Color(255, 227, 102);

    /** Color con que se muestra un botón deshabilitado. */
    private static final Color COLOR_DESHABILITADO = new Color(206, 212, 218);

    private final String titulo;

    /** Línea donde se avisa que hay una tarea corriendo en segundo plano. */
    private final JLabel etiquetaEstado = new JLabel(" ");

    /**
     * Botones que se deshabilitan mientras el módulo tiene trabajo en curso. Se
     * registran solos al armar la barra de acciones.
     */
    private final List<JButton> botonesDeAccion = new ArrayList<>();

    /**
     * Cuántas tareas de segundo plano tiene el módulo corriendo en este momento. Se
     * lee y se escribe únicamente desde el hilo de Swing, así que no necesita
     * sincronización: es un contador y no un booleano porque un módulo puede lanzar
     * una segunda tarea —por ejemplo recargar la tabla— antes de que termine la
     * primera, y los botones deben volver solo cuando no queda ninguna.
     */
    private int tareasEnCurso;

    /**
     * Crea el módulo con su título y los márgenes comunes.
     *
     * @param titulo texto que encabeza el módulo
     */
    protected PanelModulo(String titulo) {
        this.titulo = titulo;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel etiqueta = new JLabel(titulo);
        etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD, 18f));
        add(etiqueta, BorderLayout.NORTH);
    }

    /**
     * Vuelve a leer los datos del módulo desde la base de datos. La {@link MainView}
     * la llama cada vez que el módulo se muestra y después de cada cambio, para que
     * ninguna pantalla quede mostrando información vieja.
     */
    public abstract void recargar();

    /**
     * Devuelve el título del módulo, que la {@link MainView} usa como texto de su
     * botón en el menú lateral.
     *
     * @return el título con que se creó el módulo
     */
    public String getTitulo() {
        return titulo;
    }

    /**
     * Arma el área central del módulo con la parte de arriba fija —formulario y
     * botones—, la tabla ocupando el resto y la línea de estado abajo.
     *
     * @param superior componente que se mantiene arriba
     * @param centro   componente que crece con la ventana, normalmente la tabla
     */
    protected void armarContenido(JComponent superior, JComponent centro) {
        JPanel contenedor = new JPanel(new BorderLayout(10, 10));
        contenedor.add(superior, BorderLayout.NORTH);
        contenedor.add(centro, BorderLayout.CENTER);
        contenedor.add(crearLineaEstado(), BorderLayout.SOUTH);
        add(contenedor, BorderLayout.CENTER);
    }

    /**
     * Arma la línea donde se avisa qué está haciendo el módulo en segundo plano.
     * Mientras no hay nada en curso queda en blanco.
     *
     * @return el panel con la etiqueta de estado
     */
    private JPanel crearLineaEstado() {
        etiquetaEstado.setFont(etiquetaEstado.getFont().deriveFont(Font.ITALIC, 12f));
        etiquetaEstado.setForeground(new Color(90, 90, 90));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        panel.add(etiquetaEstado, BorderLayout.WEST);
        return panel;
    }

    /**
     * Crea un panel de formulario con el diseño ya preparado para usarse con
     * {@link #agregarFila(JPanel, GridBagConstraints, int, String, Component)}.
     *
     * @return el panel del formulario, vacío
     */
    protected static JPanel crearFormulario() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        return panel;
    }

    /**
     * Crea las restricciones compartidas por las filas del formulario.
     *
     * @return las restricciones del {@link GridBagLayout}
     */
    protected static GridBagConstraints crearRestricciones() {
        GridBagConstraints restricciones = new GridBagConstraints();
        restricciones.insets = new Insets(5, 5, 5, 5);
        restricciones.anchor = GridBagConstraints.LINE_START;
        return restricciones;
    }

    /**
     * Agrega una fila etiqueta + campo al formulario.
     *
     * @param panel         panel del formulario
     * @param restricciones restricciones reutilizadas del {@link GridBagLayout}
     * @param fila          número de fila
     * @param texto         texto de la etiqueta
     * @param campo         componente de entrada
     */
    protected static void agregarFila(JPanel panel, GridBagConstraints restricciones, int fila,
                                      String texto, Component campo) {
        restricciones.gridx = 0;
        restricciones.gridy = fila;
        restricciones.fill = GridBagConstraints.NONE;
        restricciones.weightx = 0;
        panel.add(new JLabel(texto), restricciones);

        restricciones.gridx = 1;
        restricciones.fill = GridBagConstraints.HORIZONTAL;
        restricciones.weightx = 1;
        panel.add(campo, restricciones);
    }

    /**
     * Crea un botón de acción con el color que le corresponde según lo que hace.
     *
     * @param texto texto del botón
     * @param fondo color de fondo, uno de los definidos en esta clase
     * @return el botón ya configurado, sin acción asociada
     */
    protected static JButton crearBoton(String texto, Color fondo) {
        JButton boton = new JButton(texto);

        boolean fondoClaro = fondo.equals(COLOR_SECUNDARIO);
        boton.setBackground(fondo);
        boton.setForeground(fondoClaro ? Color.DARK_GRAY : Color.WHITE);
        boton.setFont(boton.getFont().deriveFont(Font.BOLD));
        boton.setOpaque(true);
        boton.setBorderPainted(false);
        boton.setFocusPainted(false);
        boton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        boton.addPropertyChangeListener("enabled", cambio -> {
            boolean habilitado = Boolean.TRUE.equals(cambio.getNewValue());
            boton.setBackground(habilitado ? fondo : COLOR_DESHABILITADO);
            boton.setForeground(habilitado && !fondoClaro ? Color.WHITE : Color.DARK_GRAY);
        });
        return boton;
    }

    /**
     * Crea la barra donde van los botones de acción, pensada para ir justo encima de
     * la tabla, y registra esos botones para que se deshabiliten solos mientras el
     * módulo tiene una tarea en segundo plano.
     *
     * @param botones botones que se muestran, en el orden en que se quieren ver
     * @return el panel con los botones alineados a la izquierda
     */
    protected JPanel crearBarraDeAcciones(JButton... botones) {
        registrarBotones(botones);
        return crearBarraDeBotones(botones);
    }

    /**
     * Agrega botones a la lista de los que se deshabilitan durante las tareas de
     * segundo plano. La usan los módulos que tienen botones fuera de la barra de
     * acciones, como los del buscador.
     *
     * @param botones botones que no deben poder presionarse mientras hay trabajo en
     *                curso
     */
    protected void registrarBotones(JButton... botones) {
        botonesDeAccion.addAll(List.of(botones));
    }

    /**
     * Arma una barra de botones con el espaciado común, sin registrarlos para
     * habilitarse ni deshabilitarse. La usa la {@link VentanaFormulario}, que maneja
     * sus propios botones porque vive en una ventana aparte.
     *
     * @param botones botones que se muestran, en el orden en que se quieren ver
     * @return el panel con los botones alineados a la izquierda
     */
    protected static JPanel crearBarraDeBotones(JButton... botones) {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        barra.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        for (JButton boton : botones) {
            barra.add(boton);
        }
        return barra;
    }

    /**
     * Crea el modelo de una tabla de solo lectura: el usuario cambia los datos por el
     * formulario, nunca escribiendo sobre las celdas.
     *
     * @param columnas encabezados de la tabla
     * @return el modelo vacío, listo para llenarse
     */
    protected DefaultTableModel crearModeloTabla(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
    }

    /**
     * Crea la tabla del módulo sobre su modelo, en la que solo se puede elegir una
     * fila a la vez.
     *
     * @param modelo modelo creado con {@link #crearModeloTabla(String...)}
     * @return la tabla lista para agregarse dentro de un {@link JScrollPane}
     */
    protected JTable crearTabla(DefaultTableModel modelo) {
        JTable tabla = new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setFillsViewportHeight(true);
        tabla.setRowHeight(24);
        return tabla;
    }

    /**
     * Devuelve el identificador de la fila seleccionada, que las tablas muestran
     * siempre en su primera columna.
     *
     * @param tabla tabla del módulo
     * @return el identificador seleccionado, o {@code 0} si no hay ninguna fila elegida
     */
    protected int idSeleccionado(JTable tabla) {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            return 0;
        }
        Object valor = tabla.getValueAt(fila, 0);
        return valor instanceof Integer id ? id : 0;
    }

    /**
     * Ejecuta una acción que solo toca la pantalla y traduce los problemas en mensajes
     * para el usuario: un dato mal escrito se avisa como advertencia y un fallo de la
     * base de datos como error.
     * <p>
     * Corre en el hilo de Swing, así que <b>no debe usarse para operaciones que
     * consulten o escriban en la base de datos</b>: esas van en
     * {@link #ejecutarEnSegundoPlano(String, Supplier, Consumer)}.
     *
     * @param accion acción que se quiere ejecutar
     */
    protected void ejecutarConAvisos(Runnable accion) {
        try {
            accion.run();
        } catch (IllegalArgumentException e) {
            mostrarAviso(e.getMessage());
        } catch (PersistenciaException e) {
            mostrarError(e.getMessage());
        }
    }

    /**
     * Ejecuta una operación de la base de datos fuera del hilo de la interfaz y
     * entrega su resultado de vuelta en ese hilo.
     * <p>
     * Es el único camino por el que los módulos llaman a sus controladores. El reparto
     * es estricto: la {@code tarea} corre en el hilo del {@link SwingWorker} y solo
     * puede hablar con el controlador, nunca tocar componentes de Swing; el
     * {@code alTerminar} corre en el hilo de la interfaz y es el único que puede
     * escribir en las tablas, los combos o los mensajes. Por eso los datos que la
     * tarea necesite de la pantalla —el texto del buscador, la fila seleccionada— se
     * leen antes de llamar a este método.
     * <p>
     * Mientras la tarea dura, los botones del módulo quedan deshabilitados y el aviso
     * se muestra en la línea de estado. Si la tarea falla, el problema se informa con
     * {@link #informarFalla(Throwable)} y el {@code alTerminar} no se ejecuta.
     *
     * @param aviso      texto que se muestra mientras la tarea corre
     * @param tarea      operación que se ejecuta en segundo plano, normalmente una
     *                   llamada al controlador
     * @param alTerminar qué hacer en la interfaz con el resultado de la tarea
     * @param <T>        tipo del resultado que devuelve la tarea
     */
    protected <T> void ejecutarEnSegundoPlano(String aviso, Supplier<T> tarea,
                                              Consumer<T> alTerminar) {
        iniciarTarea(aviso);

        new SwingWorker<T, Void>() {

            @Override
            protected T doInBackground() {
                return tarea.get();
            }

            @Override
            protected void done() {
                terminarTarea();
                try {
                    T resultado = get();
                    ejecutarConAvisos(() -> alTerminar.accept(resultado));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    mostrarError("La operación quedó interrumpida.");

                } catch (ExecutionException e) {
                    informarFalla(e.getCause());
                }
            }
        }.execute();
    }

    /**
     * Anota que empezó una tarea de segundo plano: muestra el aviso y bloquea los
     * botones del módulo.
     *
     * @param aviso texto que describe lo que se está haciendo
     */
    private void iniciarTarea(String aviso) {
        tareasEnCurso++;
        etiquetaEstado.setText(aviso);
        habilitarBotones(false);
    }

    /**
     * Anota que terminó una tarea de segundo plano. Los botones vuelven y el aviso se
     * borra solo cuando ya no queda ninguna tarea en curso.
     */
    private void terminarTarea() {
        tareasEnCurso = Math.max(0, tareasEnCurso - 1);
        if (tareasEnCurso == 0) {
            etiquetaEstado.setText(" ");
            habilitarBotones(true);
        }
    }

    /**
     * Habilita o deshabilita de una vez todos los botones registrados del módulo.
     *
     * @param habilitados {@code true} para devolverle el control al usuario
     */
    private void habilitarBotones(boolean habilitados) {
        for (JButton boton : botonesDeAccion) {
            boton.setEnabled(habilitados);
        }
    }

    /**
     * Informa un problema ocurrido en una tarea de segundo plano, distinguiendo igual
     * que {@link #ejecutarConAvisos(Runnable)} entre un dato inválido y un fallo de la
     * base de datos.
     *
     * @param causa excepción original que lanzó la tarea
     */
    protected void informarFalla(Throwable causa) {
        if (causa instanceof IllegalArgumentException) {
            mostrarAviso(causa.getMessage());
        } else if (causa instanceof PersistenciaException) {
            mostrarError(causa.getMessage());
        } else {
            mostrarError("Ocurrió un problema inesperado: "
                    + (causa == null ? "sin detalle" : causa.getMessage()));
        }
    }

    /**
     * Muestra un mensaje de operación realizada.
     *
     * @param mensaje texto que se muestra
     */
    protected void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, getTitulo(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Muestra una advertencia, por ejemplo un campo obligatorio sin llenar.
     *
     * @param mensaje texto que se muestra
     */
    protected void mostrarAviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, getTitulo(),
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Muestra un error, normalmente un problema de la base de datos.
     *
     * @param mensaje texto que se muestra
     */
    protected void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, getTitulo(),
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Pide confirmación antes de una operación que no se puede deshacer.
     *
     * @param mensaje pregunta que se muestra al usuario
     * @return {@code true} si el usuario confirmó
     */
    protected boolean confirmar(String mensaje) {
        return JOptionPane.showConfirmDialog(this, mensaje, getTitulo(),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                == JOptionPane.YES_OPTION;
    }
}
