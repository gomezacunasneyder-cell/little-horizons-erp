package test.vista;

import test.controlador.BuscadorEstudiantesController;
import test.dao.EstudianteMatriculadoDao;
import test.dao.JdbcEstudianteMatriculadoDao;

/**
 * Punto de entrada directo del examen: ejecuta el buscador sin pasar por el login ni por el
 * menú principal del ERP. Aquí se arman las piezas una sola vez (inyección manual de
 * dependencias, igual que el Main del ERP): DAO -> controlador -> vista.
 *
 * Se ejecuta desde la raíz del proyecto, porque ahí está config.txt con los datos de MySQL.
 */
public class BuscadorEstudiantesMain {

    public static void main(String[] args) {
        EstudianteMatriculadoDao dao = new JdbcEstudianteMatriculadoDao();
        BuscadorEstudiantesController controlador = new BuscadorEstudiantesController(dao);
        ConsolaBuscador consola = ConsolaBuscador.estandar();

        new MenuBuscadorVista(controlador, consola).iniciar();
    }
}
