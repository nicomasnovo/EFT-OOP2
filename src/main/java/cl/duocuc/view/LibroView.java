package cl.duocuc.view;

import cl.duocuc.controllers.CategoriaController;
import cl.duocuc.controllers.LibroController;
import cl.duocuc.model.Categoria;
import cl.duocuc.model.Libro;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Módulo de gestión de libros: crear, listar, buscar, modificar y eliminar el
 * catálogo.
 * <p>
 * El panel muestra solo el buscador, los botones y la tabla. Los datos del libro se
 * escriben en una {@link VentanaFormulario} aparte, que se abre vacía al agregar y
 * cargada con la fila elegida al modificar.
 * <p>
 * La categoría se elige en un {@link JComboBox} que muestra nombres, nunca el
 * {@code id_categoria}: la ventana entrega la {@link Categoria} elegida y el
 * {@link LibroController} se encarga de traducirla. La tabla también muestra el
 * nombre, que llega del {@code JOIN} que hace el DAO.
 * <p>
 * El mismo módulo sirve para los dos roles. En modo de solo lectura —el catálogo que
 * ve un estudiante— no se arma la barra de acciones, solo el buscador y la tabla, así
 * que desde la interfaz no hay forma de abrir el formulario ni de modificar nada.
 * <p>
 * <b>Concurrencia y stock.</b> Toda consulta pasa por
 * {@link #ejecutarEnSegundoPlano(String, java.util.function.Supplier,
 * java.util.function.Consumer)}, incluidas las categorías del combo, que se leen antes
 * de abrir la ventana para que el formulario no haga JDBC mientras se arma. Al
 * modificar, el módulo recuerda el stock que mostraba la fila elegida y se lo pasa al
 * controlador: si entremedio hubo un préstamo o una devolución, el cambio se rechaza en
 * vez de pisar el número correcto.
 */
public class LibroView extends PanelModulo {

    /** Columna de la tabla donde se muestra el stock del libro. */
    private static final int COLUMNA_STOCK = 5;

    private final LibroController libroController;
    private final CategoriaController categoriaController;

    private final JTextField campoBuscar = new JTextField(18);

    private final DefaultTableModel modelo = crearModeloTabla(
            "ID", "Título", "Autor", "ISBN", "Editorial", "Stock", "Categoría");
    private final JTable tabla = crearTabla(modelo);

    /**
     * Crea el módulo de libros.
     *
     * @param libroController     controlador de libros
     * @param categoriaController controlador de categorías, que llena el combo de la
     *                            ventana del formulario
     * @param soloLectura         {@code true} para mostrar el catálogo sin permitir
     *                            cambios, como lo ve un estudiante
     */
    public LibroView(LibroController libroController, CategoriaController categoriaController,
                     boolean soloLectura) {
        super(soloLectura ? "Catálogo de libros" : "Gestión de libros");
        this.libroController = libroController;
        this.categoriaController = categoriaController;

        JPanel superior = new JPanel();
        superior.setLayout(new BoxLayout(superior, BoxLayout.Y_AXIS));
        superior.add(crearBuscador());

        if (!soloLectura) {
            superior.add(crearAcciones());
        }

        armarContenido(superior, new JScrollPane(tabla));
    }

    /**
     * Arma la línea del buscador, que filtra por título, autor o ISBN.
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
        panel.add(new JLabel("Buscar (título, autor o ISBN):"));
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
        JButton botonNuevo = crearBoton("Agregar libro", COLOR_CREAR);
        botonNuevo.addActionListener(e -> ejecutarConAvisos(this::agregar));

        JButton botonModificar = crearBoton("Modificar", COLOR_MODIFICAR);
        botonModificar.addActionListener(e -> ejecutarConAvisos(this::modificar));

        JButton botonEliminar = crearBoton("Eliminar", COLOR_ELIMINAR);
        botonEliminar.addActionListener(e -> ejecutarConAvisos(this::eliminar));

        return crearBarraDeAcciones(botonNuevo, botonModificar, botonEliminar);
    }

    /**
     * Lee las categorías en segundo plano y después abre la ventana en blanco para
     * agregar un libro.
     */
    private void agregar() {
        ejecutarEnSegundoPlano("Cargando las categorías...",
                categoriaController::listar,
                this::abrirVentanaNueva);
    }

    /**
     * Abre la ventana del formulario en blanco y agrega al catálogo el libro que se
     * escriba.
     *
     * @param categorias categorías ya leídas, con las que se llena el combo
     */
    private void abrirVentanaNueva(List<Categoria> categorias) {
        Formulario formulario = new Formulario("Nuevo libro", "Guardar", COLOR_CREAR,
                categorias);

        Boolean agregado = formulario.mostrarYGuardar(formulario::reunirDatos,
                libroController::crear);
        if (agregado == null) {
            return;
        }

        if (agregado) {
            mostrarInfo("El libro quedó agregado al catálogo.");
            recargar();
        } else {
            mostrarError("El libro no se pudo agregar.");
        }
    }

    /**
     * Toma la fila elegida en la tabla, lee las categorías en segundo plano y después
     * abre la ventana para modificar ese libro.
     */
    private void modificar() {
        int fila = tabla.getSelectedRow();
        int id = idSeleccionado(tabla);
        if (id <= 0) {
            mostrarAviso("Debe seleccionar un libro de la tabla.");
            return;
        }

        Object[] datosFila = leerFila(fila);

        ejecutarEnSegundoPlano("Cargando las categorías...",
                categoriaController::listar,
                categorias -> abrirVentanaDeEdicion(id, datosFila, categorias));
    }

    /**
     * Abre la ventana del formulario con los datos del libro seleccionado y guarda los
     * cambios.
     *
     * @param id         identificador del libro que se modifica
     * @param datosFila  valores que mostraba la fila elegida
     * @param categorias categorías ya leídas, con las que se llena el combo
     */
    private void abrirVentanaDeEdicion(int id, Object[] datosFila, List<Categoria> categorias) {
        Formulario formulario = new Formulario("Modificar libro", "Guardar cambios",
                COLOR_MODIFICAR, categorias);
        formulario.cargarDesdeFila(datosFila);

        int stockOriginal = stockDeLaFila(datosFila);

        Boolean actualizado = formulario.mostrarYGuardar(formulario::reunirDatos,
                libro -> libroController.actualizar(id, libro, stockOriginal));
        if (actualizado == null) {
            return;
        }

        if (actualizado) {
            mostrarInfo("Los datos del libro quedaron actualizados.");
        } else {
            mostrarAviso("El libro seleccionado ya no existe en el catálogo.");
        }
        recargar();
    }

    /**
     * Elimina el libro seleccionado, después de confirmar con el usuario.
     */
    private void eliminar() {
        int id = idSeleccionado(tabla);
        if (id <= 0) {
            mostrarAviso("Debe seleccionar un libro de la tabla.");
            return;
        }

        String titulo = String.valueOf(tabla.getValueAt(tabla.getSelectedRow(), 1));
        if (!confirmar("¿Eliminar el libro \"" + titulo + "\" del catálogo?")) {
            return;
        }

        ejecutarEnSegundoPlano("Eliminando el libro...",
                () -> libroController.eliminar(id),
                eliminado -> {
                    if (eliminado) {
                        mostrarInfo("El libro quedó eliminado del catálogo.");
                    } else {
                        mostrarAviso("El libro seleccionado ya no existe en el catálogo.");
                    }
                    recargar();
                });
    }

    /**
     * Vuelve a leer los libros desde la base de datos, respetando el texto que haya en
     * el buscador.
     * <p>
     * El texto se lee acá, en el hilo de la interfaz, y se le pasa ya leído a la tarea
     * de segundo plano: el hilo que hace la consulta no toca los componentes de Swing.
     */
    @Override
    public void recargar() {
        String filtro = campoBuscar.getText();

        ejecutarEnSegundoPlano("Cargando el catálogo de libros...",
                () -> libroController.buscar(filtro),
                this::mostrarEnTabla);
    }

    /**
     * Vuelca en la tabla los libros que trajo la consulta.
     *
     * @param libros libros leídos de la base de datos
     */
    private void mostrarEnTabla(List<Libro> libros) {
        modelo.setRowCount(0);
        for (Libro libro : libros) {
            modelo.addRow(new Object[]{
                    libro.getId(),
                    libro.getTitulo(),
                    libro.getAutor(),
                    libro.getIsbn(),
                    libro.getEditorial(),
                    libro.getStock(),
                    libro.getNombreCategoria()});
        }
    }

    /**
     * Copia los valores de una fila de la tabla, para seguir usándolos después de que
     * la tarea de segundo plano haya terminado.
     *
     * @param fila fila seleccionada
     * @return los valores de esa fila, en el orden de las columnas
     */
    private Object[] leerFila(int fila) {
        Object[] valores = new Object[modelo.getColumnCount()];
        for (int columna = 0; columna < valores.length; columna++) {
            valores[columna] = tabla.getValueAt(fila, columna);
        }
        return valores;
    }

    /**
     * Devuelve el stock que mostraba la fila elegida, que es el valor con que se
     * comparará el stock guardado al actualizar.
     *
     * @param datosFila valores de la fila
     * @return el stock que vio el usuario al abrir la ficha
     */
    private int stockDeLaFila(Object[] datosFila) {
        Object valor = datosFila[COLUMNA_STOCK];
        return valor instanceof Integer stock ? stock : 0;
    }

    /**
     * Ventana con los datos del libro. La misma sirve para agregar y para modificar:
     * cambian el título, el texto del botón y la operación que se le pide al
     * controlador, pero los campos son los mismos.
     * <p>
     * No consulta la base de datos: recibe las categorías ya leídas, porque la ventana
     * se arma en el hilo de la interfaz.
     */
    private final class Formulario extends VentanaFormulario {

        private final JTextField campoTitulo = new JTextField(22);
        private final JTextField campoAutor = new JTextField(22);
        private final JTextField campoIsbn = new JTextField(22);
        private final JTextField campoEditorial = new JTextField(22);
        private final JTextField campoStock = new JTextField(6);
        private final JComboBox<Categoria> comboCategorias = new JComboBox<>();

        /**
         * Arma la ventana con los datos del libro y deja el combo cargado con las
         * categorías que se leyeron antes de abrirla.
         *
         * @param titulo       título de la ventana
         * @param textoAceptar texto del botón que guarda
         * @param colorAceptar color del botón que guarda
         * @param categorias   categorías disponibles
         */
        private Formulario(String titulo, String textoAceptar, Color colorAceptar,
                           List<Categoria> categorias) {
            super(LibroView.this, titulo, textoAceptar, colorAceptar);

            agregarCampo("Título:", campoTitulo);
            agregarCampo("Autor:", campoAutor);
            agregarCampo("ISBN:", campoIsbn);
            agregarCampo("Editorial:", campoEditorial);
            agregarCampo("Stock:", campoStock);
            agregarCampo("Categoría:", comboCategorias);

            for (Categoria categoria : categorias) {
                comboCategorias.addItem(categoria);
            }
        }

        /**
         * Copia a la ventana los datos del libro elegido en la tabla.
         *
         * @param datosFila valores de la fila seleccionada
         */
        private void cargarDesdeFila(Object[] datosFila) {
            campoTitulo.setText(String.valueOf(datosFila[1]));
            campoAutor.setText(String.valueOf(datosFila[2]));
            campoIsbn.setText(String.valueOf(datosFila[3]));
            campoEditorial.setText(String.valueOf(datosFila[4]));
            campoStock.setText(String.valueOf(datosFila[COLUMNA_STOCK]));
            seleccionarCategoria(String.valueOf(datosFila[6]));
        }

        /**
         * Deja elegida en el combo la categoría que tiene el libro seleccionado.
         *
         * @param nombre nombre de la categoría que muestra la tabla
         */
        private void seleccionarCategoria(String nombre) {
            for (int posicion = 0; posicion < comboCategorias.getItemCount(); posicion++) {
                if (comboCategorias.getItemAt(posicion).getNombre().equals(nombre)) {
                    comboCategorias.setSelectedIndex(posicion);
                    return;
                }
            }
        }

        /**
         * Lee los campos de la ventana y arma con ellos el libro validado que se va a
         * guardar. Se ejecuta en el hilo de la interfaz, que es el que puede leerlos.
         *
         * @return el libro con los datos escritos
         * @throws IllegalArgumentException si falta un dato o el stock no es válido
         */
        private Libro reunirDatos() {
            return libroController.validar(campoTitulo.getText(), campoAutor.getText(),
                    campoIsbn.getText(), campoEditorial.getText(), campoStock.getText(),
                    (Categoria) comboCategorias.getSelectedItem());
        }
    }
}
