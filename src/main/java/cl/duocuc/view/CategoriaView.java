package cl.duocuc.view;

import cl.duocuc.controllers.CategoriaController;
import cl.duocuc.model.Categoria;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Módulo de gestión de categorías: crear, listar, modificar y eliminar las
 * categorías temáticas con que se clasifican los libros.
 * <p>
 * Existe porque el módulo de libros no pide un {@code id_categoria} sino que ofrece
 * un {@code JComboBox} con nombres, y esos nombres se administran aquí. Una categoría
 * con libros asociados no se puede eliminar: la clave foránea
 * {@code libros.id_categoria} lo impide y el DAO devuelve el motivo.
 * <p>
 * Es el único módulo que mantiene su formulario sobre la tabla, porque es un solo
 * campo y no vale abrir una ventana para eso. Igual que los demás, lee ese campo en el
 * hilo de la interfaz y recién después manda la operación a segundo plano con
 * {@link #ejecutarEnSegundoPlano(String, java.util.function.Supplier,
 * java.util.function.Consumer)}.
 */
public class CategoriaView extends PanelModulo {

    private final CategoriaController categoriaController;

    private final JTextField campoNombre = new JTextField(22);

    private final DefaultTableModel modelo = crearModeloTabla("ID", "Nombre");
    private final JTable tabla = crearTabla(modelo);

    /**
     * Crea el módulo de categorías.
     *
     * @param categoriaController controlador de categorías
     */
    public CategoriaView(CategoriaController categoriaController) {
        super("Gestión de categorías");
        this.categoriaController = categoriaController;

        JPanel superior = new JPanel();
        superior.setLayout(new BoxLayout(superior, BoxLayout.Y_AXIS));
        superior.add(crearFormularioCategoria());
        superior.add(crearAcciones());

        tabla.getSelectionModel().addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting()) {
                cargarSeleccionEnFormulario();
            }
        });

        armarContenido(superior, new JScrollPane(tabla));
    }

    /**
     * Arma el formulario, que para esta tabla es un solo campo.
     *
     * @return el panel del formulario
     */
    private JPanel crearFormularioCategoria() {
        JPanel formulario = crearFormulario();
        GridBagConstraints restricciones = crearRestricciones();

        agregarFila(formulario, restricciones, 0, "Nombre:", campoNombre);
        return formulario;
    }

    /**
     * Arma la barra con las acciones del CRUD.
     *
     * @return el panel con los botones
     */
    private JPanel crearAcciones() {
        JButton botonNueva = crearBoton("Agregar categoría", COLOR_CREAR);
        botonNueva.addActionListener(e -> ejecutarConAvisos(this::agregar));

        JButton botonModificar = crearBoton("Modificar", COLOR_MODIFICAR);
        botonModificar.addActionListener(e -> ejecutarConAvisos(this::modificar));

        JButton botonEliminar = crearBoton("Eliminar", COLOR_ELIMINAR);
        botonEliminar.addActionListener(e -> ejecutarConAvisos(this::eliminar));

        JButton botonLimpiar = crearBoton("Limpiar", COLOR_SECUNDARIO);
        botonLimpiar.addActionListener(e -> limpiarFormulario());

        return crearBarraDeAcciones(botonNueva, botonModificar, botonEliminar, botonLimpiar);
    }

    /**
     * Agrega la categoría escrita en el formulario.
     */
    private void agregar() {
        String nombre = campoNombre.getText();

        ejecutarEnSegundoPlano("Guardando la categoría...",
                () -> categoriaController.crear(nombre),
                agregada -> {
                    if (agregada) {
                        mostrarInfo("La categoría quedó agregada.");
                        limpiarFormulario();
                        recargar();
                    } else {
                        mostrarError("La categoría no se pudo agregar.");
                    }
                });
    }

    /**
     * Cambia el nombre de la categoría seleccionada.
     */
    private void modificar() {
        int id = idSeleccionado(tabla);
        String nombre = campoNombre.getText();

        ejecutarEnSegundoPlano("Guardando el nombre nuevo...",
                () -> categoriaController.actualizar(id, nombre),
                actualizada -> {
                    if (actualizada) {
                        mostrarInfo("El nombre de la categoría quedó actualizado.");
                    } else {
                        mostrarAviso("La categoría seleccionada ya no existe.");
                    }
                    recargar();
                });
    }

    /**
     * Elimina la categoría seleccionada, después de confirmar con el usuario.
     */
    private void eliminar() {
        int id = idSeleccionado(tabla);
        if (id <= 0) {
            mostrarAviso("Debe seleccionar una categoría de la tabla.");
            return;
        }

        String nombre = String.valueOf(tabla.getValueAt(tabla.getSelectedRow(), 1));
        if (!confirmar("¿Eliminar la categoría \"" + nombre + "\"?")) {
            return;
        }

        ejecutarEnSegundoPlano("Eliminando la categoría...",
                () -> categoriaController.eliminar(id),
                eliminada -> {
                    if (eliminada) {
                        mostrarInfo("La categoría quedó eliminada.");
                        limpiarFormulario();
                    } else {
                        mostrarAviso("La categoría seleccionada ya no existe.");
                    }
                    recargar();
                });
    }

    /**
     * Copia al formulario el nombre de la categoría elegida en la tabla.
     */
    private void cargarSeleccionEnFormulario() {
        int fila = tabla.getSelectedRow();
        if (fila >= 0) {
            campoNombre.setText(String.valueOf(tabla.getValueAt(fila, 1)));
        }
    }

    /**
     * Deja el formulario en blanco y suelta la fila seleccionada.
     */
    private void limpiarFormulario() {
        campoNombre.setText("");
        tabla.clearSelection();
    }

    /**
     * Vuelve a leer las categorías desde la base de datos.
     */
    @Override
    public void recargar() {
        ejecutarEnSegundoPlano("Cargando las categorías...",
                categoriaController::listar,
                categorias -> {
                    modelo.setRowCount(0);
                    for (Categoria categoria : categorias) {
                        modelo.addRow(new Object[]{categoria.getId(), categoria.getNombre()});
                    }
                });
    }
}
