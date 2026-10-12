package cl.duocuc.main;

import cl.duocuc.conexion.DatabaseConnection;
import cl.duocuc.controllers.Controladores;
import cl.duocuc.view.LoginView;

import javax.swing.*;

/**
 * Punto de entrada del Sistema de Gestión de Biblioteca Escolar.
 * <p>
 * Hace tres cosas y nada más: comprueba con el Singleton
 * {@link DatabaseConnection#validarConexion()} que la base de datos {@code biblioteca}
 * responda, crea los controladores que compartirá toda la aplicación y abre la ventana
 * de inicio de sesión dentro del hilo de Swing.
 * <p>
 * Si la base de datos no responde no se abre el login, porque sin ella no hay usuarios
 * con los que autenticarse: no existe ninguna credencial escrita en el código, todas
 * vienen del script de poblado.
 * <p>
 * Esta clase no usa JDBC directamente: no conoce {@code Connection} ni SQL, solo el
 * resultado de la validación.
 */
public class Main {

    /**
     * Inicia la aplicación.
     *
     * @param args argumentos de línea de comandos; no se utilizan
     */
    public static void main(String[] args) {

        if (!DatabaseConnection.getInstance().validarConexion()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "No se pudo conectar con la base de datos 'biblioteca'.\n\n"
                                + "Revise que MySQL esté corriendo y que los scripts de la\n"
                                + "carpeta db/ se hayan ejecutado antes de abrir el sistema.",
                        "Biblioteca Escolar", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
            return;
        }

        Controladores controladores = Controladores.crear();

        SwingUtilities.invokeLater(() -> new LoginView(controladores).setVisible(true));
    }
}
