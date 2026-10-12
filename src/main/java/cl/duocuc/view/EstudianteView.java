package cl.duocuc.view;

import cl.duocuc.controllers.EstudianteController;
import cl.duocuc.model.Estudiante;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Módulo de gestión de estudiantes: crear, listar, buscar, modificar y eliminar las
 * fichas de la tabla {@code estudiantes}.
 * <p>
 * El panel muestra solo el buscador, los botones y la tabla. Los datos del estudiante
 * se escriben en una {@link VentanaFormulario} aparte, que se abre vacía al agregar y
 * cargada con la fila elegida al modificar.
 * <p>
 * Solo lo ve el bibliotecario. Un estudiante con préstamos registrados no se puede
 * eliminar, porque su historial lo referencia; en ese caso el DAO avisa el motivo y el
 * módulo lo muestra tal cual.
 * <p>
 * <b>Concurrencia.</b> El listado, la búsqueda, el guardado y la eliminación se
 * ejecutan fuera del hilo de la interfaz, con los ayudantes del {@link PanelModulo} y
 * de la {@link VentanaFormulario}. Lo que se lee de la pantalla —el texto del buscador,
 * los campos de la ventana, la fila elegida— se lee siempre en el hilo de Swing, antes
 * de mandar la operación a segundo plano.
 */
public class EstudianteView extends PanelModulo {

    private final EstudianteController estudianteController;

    private final JTextField campoBuscar = new JTextField(18);

    private final DefaultTableModel modelo = crearModeloTabla(
            "ID", "Nombre", "RUT", "Curso", "Correo");
    private final JTable tabla = crearTabla(modelo);

    /**
     * Crea el módulo de estudiantes.
     *
     * @param estudianteController controlador de estudiantes
     */
    public EstudianteView(EstudianteController estudianteController) {
        super("Gestión de estudiantes");
        this.estudianteController = estudianteController;

        JPanel superior = new JPanel();
        superior.setLayout(new BoxLayout(superior, BoxLayout.Y_AXIS));
        superior.add(crearBuscador());
        superior.add(crearAcciones());

        armarContenido(superior, new JScrollPane(tabla));
    }

    /**
     * Arma la línea del buscador, que filtra por nombre, RUT o curso.
     *
     * @return el panel del buscador
     */
    private JPanel crearBuscador() {
        JButton botonBuscar = crearBoton("Buscar", COLOR_SECUNDARIO);
        botonBuscar.addActionListener(e -> recargar());

        JButton botonVerTodos = crearBoton("Ver todos", COLOR_SECUNDARIO);
        botonVerTodos.addActionListener(e -> {
            campoBuscar.setText("");
            recargar();
        });

        campoBuscar.addActionListener(e -> recargar());
        registrarBotones(botonBuscar, botonVerTodos);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        panel.add(new JLabel("Buscar (nombre, RUT o curso):"));
        panel.add(campoBuscar);
        panel.add(botonBuscar);
        panel.add(botonVerTodos);
        return panel;
    }

    /**
     * Arma la barra con las acciones del CRUD.
     *
     * @return el panel con los botones
     */
    private JPanel crearAcciones() {
        JButton botonNuevo = crearBoton("Agregar estudiante", COLOR_CREAR);
        botonNuevo.addActionListener(e -> ejecutarConAvisos(this::agregar));

        JButton botonModificar = crearBoton("Modificar", COLOR_MODIFICAR);
        botonModificar.addActionListener(e -> ejecutarConAvisos(this::modificar));

        JButton botonEliminar = crearBoton("Eliminar", COLOR_ELIMINAR);
        botonEliminar.addActionListener(e -> ejecutarConAvisos(this::eliminar));

        return crearBarraDeAcciones(botonNuevo, botonModificar, botonEliminar);
    }

    /**
     * Abre la ventana del formulario en blanco y registra el estudiante que se escriba.
     * La ventana se encarga de guardar en segundo plano y de cerrarse solo cuando la
     * base de datos confirma.
     */
    private void agregar() {
        Formulario formulario = new Formulario("Nuevo estudiante", "Guardar", COLOR_CREAR);

        Boolean registrado = formulario.mostrarYGuardar(formulario::reunirDatos,
                estudianteController::crear);
        if (registrado == null) {
            return;
        }

        if (registrado) {
            mostrarInfo("El estudiante quedó registrado.");
            recargar();
        } else {
            mostrarError("El estudiante no se pudo registrar.");
        }
    }

    /**
     * Abre la ventana del formulario con los datos del estudiante seleccionado y guarda
     * los cambios.
     */
    private void modificar() {
        int fila = tabla.getSelectedRow();
        int id = idSeleccionado(tabla);
        if (id <= 0) {
            mostrarAviso("Debe seleccionar un estudiante de la tabla.");
            return;
        }

        Formulario formulario = new Formulario("Modificar estudiante", "Guardar cambios",
                COLOR_MODIFICAR);
        formulario.cargarDesdeTabla(fila);

        Boolean actualizado = formulario.mostrarYGuardar(formulario::reunirDatos,
                estudiante -> estudianteController.actualizar(id, estudiante));
        if (actualizado == null) {
            return;
        }

        if (actualizado) {
            mostrarInfo("Los datos del estudiante quedaron actualizados.");
        } else {
            mostrarAviso("El estudiante seleccionado ya no existe.");
        }
        recargar();
    }

    /**
     * Elimina el estudiante seleccionado, después de confirmar con el usuario.
     */
    private void eliminar() {
        int id = idSeleccionado(tabla);
        if (id <= 0) {
            mostrarAviso("Debe seleccionar un estudiante de la tabla.");
            return;
        }

        String nombre = String.valueOf(tabla.getValueAt(tabla.getSelectedRow(), 1));
        if (!confirmar("¿Eliminar la ficha de " + nombre + "?")) {
            return;
        }

        ejecutarEnSegundoPlano("Eliminando la ficha...",
                () -> estudianteController.eliminar(id),
                eliminado -> {
                    if (eliminado) {
                        mostrarInfo("El estudiante quedó eliminado.");
                    } else {
                        mostrarAviso("El estudiante seleccionado ya no existe.");
                    }
                    recargar();
                });
    }

    /**
     * Vuelve a leer los estudiantes desde la base de datos, respetando el texto que
     * haya en el buscador.
     * <p>
     * El texto se lee acá, en el hilo de la interfaz, y se le pasa ya leído a la tarea
     * de segundo plano: el hilo que hace la consulta no toca los componentes de Swing.
     */
    @Override
    public void recargar() {
        String filtro = campoBuscar.getText();

        ejecutarEnSegundoPlano("Cargando los estudiantes...",
                () -> estudianteController.buscar(filtro),
                this::mostrarEnTabla);
    }

    /**
     * Vuelca en la tabla los estudiantes que trajo la consulta.
     *
     * @param estudiantes estudiantes leídos de la base de datos
     */
    private void mostrarEnTabla(List<Estudiante> estudiantes) {
        modelo.setRowCount(0);
        for (Estudiante estudiante : estudiantes) {
            modelo.addRow(new Object[]{
                    estudiante.getId(),
                    estudiante.getNombre(),
                    estudiante.getRut(),
                    estudiante.getCurso(),
                    estudiante.getCorreo()});
        }
    }

    /**
     * Ventana con los datos del estudiante. La misma sirve para agregar y para
     * modificar: cambian el título, el texto del botón y la operación que se le pide al
     * controlador, pero los campos son los mismos.
     */
    private final class Formulario extends VentanaFormulario {

        private final JTextField campoNombre = new JTextField(22);
        private final JTextField campoRut = new JTextField(12);
        private final JTextField campoCurso = new JTextField(14);
        private final JTextField campoCorreo = new JTextField(22);

        /**
         * Arma la ventana con las cuatro filas de la ficha.
         *
         * @param titulo       título de la ventana
         * @param textoAceptar texto del botón que guarda
         * @param colorAceptar color del botón que guarda
         */
        private Formulario(String titulo, String textoAceptar, Color colorAceptar) {
            super(EstudianteView.this, titulo, textoAceptar, colorAceptar);

            agregarCampo("Nombre:", campoNombre);
            agregarCampo("RUT:", campoRut);
            agregarCampo("Curso:", campoCurso);
            agregarCampo("Correo:", campoCorreo);
        }

        /**
         * Copia a la ventana los datos del estudiante elegido en la tabla.
         *
         * @param fila fila seleccionada
         */
        private void cargarDesdeTabla(int fila) {
            campoNombre.setText(String.valueOf(tabla.getValueAt(fila, 1)));
            campoRut.setText(String.valueOf(tabla.getValueAt(fila, 2)));
            campoCurso.setText(String.valueOf(tabla.getValueAt(fila, 3)));
            campoCorreo.setText(String.valueOf(tabla.getValueAt(fila, 4)));
        }

        /**
         * Lee los campos de la ventana y arma con ellos la ficha validada que se va a
         * guardar. Se ejecuta en el hilo de la interfaz, que es el que puede leerlos.
         *
         * @return el estudiante con los datos escritos
         * @throws IllegalArgumentException si falta un dato o el RUT o el correo no son
         *                                 válidos
         */
        private Estudiante reunirDatos() {
            return estudianteController.validar(campoNombre.getText(), campoRut.getText(),
                    campoCurso.getText(), campoCorreo.getText());
        }
    }
}
