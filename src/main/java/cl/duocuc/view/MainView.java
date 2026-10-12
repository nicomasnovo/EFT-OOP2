package cl.duocuc.view;

import cl.duocuc.controllers.Controladores;
import cl.duocuc.controllers.LoginController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ventana principal del sistema. Es la única ventana mientras hay sesión abierta:
 * tiene un menú lateral fijo a la izquierda y un área central con {@link CardLayout}
 * donde se muestran los módulos, de modo que navegar no abre ventanas nuevas.
 * <p>
 * Es también donde se aplica el <b>control de roles</b>. Los módulos se arman según el
 * rol de la sesión: el bibliotecario recibe los cinco (libros, categorías,
 * estudiantes, préstamos y reportes) y el estudiante solo el catálogo en modo de
 * consulta y sus propios préstamos. Lo que no corresponde a un rol no se construye, así
 * que no hay forma de llegar a esos módulos desde la interfaz.
 * <p>
 * Los módulos se guardan en un mapa de {@link PanelModulo} y la navegación los trata a
 * todos igual: al mostrar uno le pide {@code recargar()} sin saber cuál es.
 */
public class MainView extends JFrame {

    /** Ancho fijo del menú lateral y alto común de sus botones. */
    private static final int ANCHO_MENU = 230;
    private static final int ALTO_BOTON = 44;
    private static final int SEPARACION_BOTONES = 10;

    /** Color de fondo del menú lateral. */
    private static final Color COLOR_MENU = new Color(236, 239, 244);

    private final LoginController.Sesion sesion;
    private final Controladores controladores;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel panelContenido = new JPanel(cardLayout);

    /** Módulos disponibles para la sesión, en el orden en que se muestran en el menú. */
    private final Map<String, PanelModulo> modulos = new LinkedHashMap<>();

    /**
     * Crea la ventana principal para la sesión que se acaba de abrir.
     *
     * @param sesion        sesión con el usuario autenticado y su rol
     * @param controladores controladores compartidos de la aplicación
     */
    public MainView(LoginController.Sesion sesion, Controladores controladores) {
        this.sesion = sesion;
        this.controladores = controladores;

        setTitle("Sistema de Gestión de Biblioteca Escolar");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        armarModulos();

        add(crearEncabezado(), BorderLayout.NORTH);
        add(crearMenuLateral(), BorderLayout.WEST);
        add(panelContenido, BorderLayout.CENTER);
        add(crearPiePagina(), BorderLayout.SOUTH);

        mostrar(modulos.keySet().iterator().next());

        setMinimumSize(new Dimension(980, 600));
        setSize(1040, 640);
        setLocationRelativeTo(null);
    }

    /**
     * Crea los módulos que corresponden al rol de la sesión y los agrega al área
     * central.
     */
    private void armarModulos() {
        if (sesion.puedeAdministrar()) {
            agregarModulo(new LibroView(controladores.libros(), controladores.categorias(), false));
            agregarModulo(new CategoriaView(controladores.categorias()));
            agregarModulo(new EstudianteView(controladores.estudiantes()));
            agregarModulo(new PrestamoView(controladores.prestamos(),
                    controladores.estudiantes(), controladores.libros()));
            agregarModulo(new ReporteView(controladores.reportes(),
                    controladores.estudiantes(), null));
        } else {
            agregarModulo(new LibroView(controladores.libros(), controladores.categorias(), true));

            if (sesion.ficha() != null) {
                agregarModulo(new ReporteView(controladores.reportes(),
                        controladores.estudiantes(), sesion.ficha()));
            }
        }
    }

    /**
     * Registra un módulo en el mapa y lo agrega como tarjeta del {@link CardLayout},
     * usando su título como nombre de la tarjeta y del botón del menú.
     *
     * @param modulo módulo que se quiere mostrar
     */
    private void agregarModulo(PanelModulo modulo) {
        modulos.put(modulo.getTitulo(), modulo);
        panelContenido.add(modulo, modulo.getTitulo());
    }

    /**
     * Arma el encabezado con el nombre del sistema y la sesión abierta.
     *
     * @return el panel del encabezado
     */
    private JPanel crearEncabezado() {
        JLabel titulo = new JLabel("Sistema de Gestión de Biblioteca Escolar");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));

        JLabel usuario = new JLabel("Sesión: " + sesion.usuario().getDescripcion());
        usuario.setFont(usuario.getFont().deriveFont(12f));
        usuario.setForeground(new Color(90, 90, 90));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));
        panel.add(titulo, BorderLayout.WEST);
        panel.add(usuario, BorderLayout.EAST);
        return panel;
    }

    /**
     * Arma el menú lateral con un botón por módulo disponible y, abajo, las acciones de
     * la sesión.
     *
     * @return el panel del menú lateral
     */
    private JPanel crearMenuLateral() {
        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBackground(COLOR_MENU);
        menu.setPreferredSize(new Dimension(ANCHO_MENU, 0));
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(20, 15, 20, 15)));

        for (String nombre : modulos.keySet()) {
            agregarBoton(menu, nombre, e -> mostrar(nombre));
        }

        menu.add(Box.createVerticalGlue());
        agregarBoton(menu, "Cerrar sesión", e -> cerrarSesion());
        agregarBoton(menu, "Salir", e -> salir());
        return menu;
    }

    /**
     * Agrega al menú lateral un botón con el tamaño común y la acción que ejecuta.
     *
     * @param menu   panel del menú lateral
     * @param texto  texto del botón
     * @param accion acción que ejecuta el botón
     */
    private void agregarBoton(JPanel menu, String texto, ActionListener accion) {
        JButton boton = new JButton(texto);
        boton.setAlignmentX(Component.CENTER_ALIGNMENT);
        boton.setMaximumSize(new Dimension(Integer.MAX_VALUE, ALTO_BOTON));
        boton.setPreferredSize(new Dimension(ANCHO_MENU, ALTO_BOTON));
        boton.setFocusPainted(false);
        boton.addActionListener(accion);

        menu.add(boton);
        menu.add(Box.createVerticalStrut(SEPARACION_BOTONES));
    }

    /**
     * Arma la línea inferior con la identificación de la actividad.
     *
     * @return el panel del pie de página
     */
    private JPanel crearPiePagina() {
        JLabel etiqueta = new JLabel("Programación Orientada a Objetos 2 - Semana 9");
        etiqueta.setFont(etiqueta.getFont().deriveFont(11f));
        etiqueta.setForeground(Color.GRAY);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)));
        panel.add(etiqueta, BorderLayout.WEST);
        return panel;
    }

    /**
     * Muestra un módulo en el área central y le pide que vuelva a leer sus datos, para
     * que el usuario vea siempre la información vigente.
     * <p>
     * El módulo se muestra <b>antes</b> de pedirle los datos: su {@code recargar()}
     * consulta la base de datos en segundo plano, así que el cambio de pantalla es
     * inmediato y la tabla se llena cuando la consulta responde, con el aviso de carga a
     * la vista en el módulo que ya está en pantalla.
     *
     * @param nombre título del módulo que se quiere mostrar
     */
    private void mostrar(String nombre) {
        PanelModulo modulo = modulos.get(nombre);
        if (modulo == null) {
            return;
        }
        cardLayout.show(panelContenido, nombre);
        modulo.recargar();
    }

    /**
     * Cierra la sesión y vuelve a la ventana de inicio de sesión.
     */
    private void cerrarSesion() {
        if (!confirmar("¿Cerrar la sesión de " + sesion.usuario().getNombre() + "?")) {
            return;
        }
        dispose();
        new LoginView(controladores).setVisible(true);
    }

    /**
     * Cierra la aplicación después de confirmar con el usuario.
     */
    private void salir() {
        if (confirmar("¿Salir del sistema de biblioteca?")) {
            dispose();
            System.exit(0);
        }
    }

    /**
     * Pide confirmación al usuario antes de cerrar la sesión o la aplicación.
     *
     * @param mensaje pregunta que se muestra
     * @return {@code true} si el usuario confirmó
     */
    private boolean confirmar(String mensaje) {
        return JOptionPane.showConfirmDialog(this, mensaje, "Confirmar",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                == JOptionPane.YES_OPTION;
    }
}
