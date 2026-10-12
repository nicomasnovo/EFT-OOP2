package cl.duocuc.view;

import cl.duocuc.controllers.Controladores;
import cl.duocuc.controllers.LoginController;
import cl.duocuc.exception.PersistenciaException;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * Ventana de inicio de sesión. Pide el correo y la contraseña y se los entrega al
 * {@link LoginController}, que los valida contra la tabla {@code usuarios}.
 * <p>
 * No sabe nada de SQL ni de roles: solo muestra el formulario, informa si las
 * credenciales no son correctas y, cuando la sesión se abre, se cierra y deja en su
 * lugar la {@link MainView}, que es la que decide qué módulos corresponden al rol.
 * <p>
 * <b>Concurrencia.</b> La autenticación consulta la tabla {@code usuarios}, así que
 * corre en un {@link SwingWorker} igual que el resto de las operaciones de la
 * aplicación. Es la primera consulta que se hace y la que puede tardar más, porque es
 * la que abre la conexión: si se ejecutara en el hilo de la interfaz, la ventana de
 * ingreso quedaría congelada justo en el arranque. Mientras dura, el botón se bloquea
 * y la ventana avisa que está verificando.
 */
public class LoginView extends JFrame {

    private final Controladores controladores;

    private final JTextField campoCorreo = new JTextField(20);
    private final JPasswordField campoContrasena = new JPasswordField(20);

    private final JButton botonIngresar = new JButton("Ingresar");

    /** Línea donde se avisa que las credenciales se están verificando. */
    private final JLabel etiquetaEstado = new JLabel(" ");

    /**
     * Crea la ventana de inicio de sesión.
     *
     * @param controladores controladores compartidos de la aplicación
     */
    public LoginView(Controladores controladores) {
        this.controladores = controladores;

        setTitle("Biblioteca Escolar - Inicio de sesión");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(crearEncabezado(), BorderLayout.NORTH);
        add(crearFormulario(), BorderLayout.CENTER);
        add(crearBotones(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(430, getHeight()));
        setSize(430, getHeight());
        setResizable(false);
        setLocationRelativeTo(null);
    }

    /**
     * Arma el encabezado con el nombre del sistema.
     *
     * @return el panel del encabezado
     */
    private JPanel crearEncabezado() {
        JLabel titulo = new JLabel("Sistema de Gestión de Biblioteca Escolar");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));

        JLabel subtitulo = new JLabel("Ingrese con su correo institucional");
        subtitulo.setFont(subtitulo.getFont().deriveFont(12f));
        subtitulo.setForeground(Color.GRAY);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(18, 20, 14, 20)));
        panel.add(titulo, BorderLayout.NORTH);
        panel.add(subtitulo, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Arma el formulario con los dos campos de las credenciales.
     *
     * @return el panel del formulario
     */
    private JPanel crearFormulario() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(18, 20, 10, 20));

        GridBagConstraints restricciones = new GridBagConstraints();
        restricciones.insets = new Insets(6, 5, 6, 5);
        restricciones.anchor = GridBagConstraints.LINE_START;

        restricciones.gridx = 0;
        restricciones.gridy = 0;
        panel.add(new JLabel("Correo:"), restricciones);
        restricciones.gridx = 1;
        restricciones.fill = GridBagConstraints.HORIZONTAL;
        restricciones.weightx = 1;
        panel.add(campoCorreo, restricciones);

        restricciones.gridx = 0;
        restricciones.gridy = 1;
        restricciones.fill = GridBagConstraints.NONE;
        restricciones.weightx = 0;
        panel.add(new JLabel("Contraseña:"), restricciones);
        restricciones.gridx = 1;
        restricciones.fill = GridBagConstraints.HORIZONTAL;
        restricciones.weightx = 1;
        panel.add(campoContrasena, restricciones);

        return panel;
    }

    /**
     * Arma los botones de la ventana. "Ingresar" queda como botón por omisión, de modo
     * que la tecla Enter inicie sesión.
     *
     * @return el panel con los botones
     */
    private JPanel crearBotones() {
        botonIngresar.setBackground(new Color(40, 167, 69));
        botonIngresar.setForeground(Color.WHITE);
        botonIngresar.setFont(botonIngresar.getFont().deriveFont(Font.BOLD));
        botonIngresar.setOpaque(true);
        botonIngresar.setBorderPainted(false);
        botonIngresar.setFocusPainted(false);
        botonIngresar.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        botonIngresar.addActionListener(e -> ingresar());

        JButton botonSalir = new JButton("Salir");
        botonSalir.setFocusPainted(false);
        botonSalir.addActionListener(e -> System.exit(0));

        etiquetaEstado.setFont(etiquetaEstado.getFont().deriveFont(Font.ITALIC, 12f));
        etiquetaEstado.setForeground(Color.GRAY);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        botones.add(botonSalir);
        botones.add(botonIngresar);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 20, 4, 10)));
        panel.add(etiquetaEstado, BorderLayout.WEST);
        panel.add(botones, BorderLayout.EAST);

        getRootPane().setDefaultButton(botonIngresar);
        return panel;
    }

    /**
     * Verifica las credenciales en segundo plano y, si son correctas, abre la ventana
     * principal con el rol que le corresponde al usuario.
     * <p>
     * Los dos campos se leen acá, en el hilo de la interfaz; la consulta a la tabla
     * {@code usuarios} corre en el hilo del {@link SwingWorker} y el resultado se
     * atiende en {@code done()}, de vuelta en el hilo de la interfaz.
     */
    private void ingresar() {
        String correo = campoCorreo.getText();
        String contrasena = new String(campoContrasena.getPassword());

        verificando(true);

        new SwingWorker<LoginController.Sesion, Void>() {

            @Override
            protected LoginController.Sesion doInBackground() {
                return controladores.login().autenticar(correo, contrasena);
            }

            @Override
            protected void done() {
                verificando(false);
                try {
                    abrirSesion(get());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    avisar("El inicio de sesión quedó interrumpido.",
                            JOptionPane.ERROR_MESSAGE);

                } catch (ExecutionException e) {
                    avisarFalla(e.getCause());
                }
            }
        }.execute();
    }

    /**
     * Abre la ventana principal con la sesión que devolvió el controlador, o avisa que
     * las credenciales no eran correctas.
     *
     * @param sesion sesión autenticada, o {@code null} si el correo o la contraseña no
     *               coinciden
     */
    private void abrirSesion(LoginController.Sesion sesion) {
        if (sesion == null) {
            campoContrasena.setText("");
            avisar("El correo o la contraseña no son correctos.",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!sesion.puedeAdministrar() && sesion.ficha() == null) {
            avisar("Su cuenta no tiene una ficha de estudiante registrada, "
                            + "así que solo podrá consultar el catálogo de libros.",
                    JOptionPane.INFORMATION_MESSAGE);
        }

        dispose();
        new MainView(sesion, controladores).setVisible(true);
    }

    /**
     * Bloquea o devuelve el botón de ingreso según si hay una verificación en curso,
     * para que las credenciales no se manden dos veces.
     *
     * @param enCurso {@code true} mientras se consulta la base de datos
     */
    private void verificando(boolean enCurso) {
        botonIngresar.setEnabled(!enCurso);
        etiquetaEstado.setText(enCurso ? "Verificando las credenciales..." : " ");
    }

    /**
     * Muestra el problema que devolvió la autenticación, separando un dato mal escrito
     * de un fallo de la base de datos.
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
     * Muestra un mensaje sobre la ventana de inicio de sesión.
     *
     * @param mensaje texto que se muestra
     * @param tipo    tipo de mensaje de {@link JOptionPane}
     */
    private void avisar(String mensaje, int tipo) {
        JOptionPane.showMessageDialog(this, mensaje, "Inicio de sesión", tipo);
    }
}
