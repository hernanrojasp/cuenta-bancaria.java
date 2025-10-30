import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CuentaBancariaApp {

    /* ==============================
       === MODELO DE DOMINIO ========
       ============================== */

    public static class CuentaBancaria {
        private static final AtomicInteger SEQ = new AtomicInteger(1);

        public enum TipoCuenta { CORRIENTE, AHORROS }

        private final int id;
        private final String cliente;
        private final TipoCuenta tipo;
        private double saldo;
        private final List<Transaccion> historial = new ArrayList<>();

        public CuentaBancaria(String cliente, TipoCuenta tipo, double saldoInicial) {
            this.id = SEQ.getAndIncrement();
            this.cliente = Objects.requireNonNull(cliente, "Cliente no puede ser null");
            this.tipo = Objects.requireNonNull(tipo, "Tipo de cuenta no puede ser null");
            this.saldo = Math.max(0.0, saldoInicial);
            registrarTransaccion("Apertura de cuenta", saldoInicial);
        }

        public int getId() { return id; }
        public String getCliente() { return cliente; }
        public TipoCuenta getTipo() { return tipo; }
        public synchronized double getSaldo() { return saldo; }

        public synchronized void depositar(double cantidad) {
            if (cantidad <= 0) throw new IllegalArgumentException("La cantidad a depositar debe ser mayor que 0");
            saldo += cantidad;
            registrarTransaccion("Depósito", cantidad);
        }

        public synchronized void retirar(double cantidad) throws InsufficientFundsException {
            if (cantidad <= 0) throw new IllegalArgumentException("La cantidad a retirar debe ser mayor que 0");
            if (cantidad > saldo) throw new InsufficientFundsException("Saldo insuficiente");
            saldo -= cantidad;
            registrarTransaccion("Retiro", -cantidad);
        }

        public synchronized void registrarTransaccion(String descripcion, double monto) {
            historial.add(new Transaccion(descripcion, monto, saldo));
        }

        public List<Transaccion> getHistorial() {
            return Collections.unmodifiableList(historial);
        }

        @Override
        public String toString() {
            return String.format("ID:%d - %s (%s) - Saldo: %.2f", id, cliente, tipo, saldo);
        }

        public static class InsufficientFundsException extends Exception {
            public InsufficientFundsException(String msg) { super(msg); }
        }
    }

    /* ==============================
       === ENTIDAD TRANSACCIÓN ======
       ============================== */

    public static class Transaccion {
        private final String descripcion;
        private final double monto;
        private final double saldoPosterior;
        private final LocalDateTime fecha = LocalDateTime.now();

        public Transaccion(String descripcion, double monto, double saldoPosterior) {
            this.descripcion = descripcion;
            this.monto = monto;
            this.saldoPosterior = saldoPosterior;
        }

        @Override
        public String toString() {
            return String.format("[%s] %-20s Monto: %.2f | Saldo: %.2f",
                    fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    descripcion, monto, saldoPosterior);
        }
    }

    /* ==============================
       === REPOSITORIO BANCO ========
       ============================== */

    public static class Banco {
        private final Map<Integer, CuentaBancaria> cuentas = new LinkedHashMap<>();

        public CuentaBancaria crearCuenta(String cliente, CuentaBancaria.TipoCuenta tipo, double saldoInicial) {
            CuentaBancaria c = new CuentaBancaria(cliente, tipo, saldoInicial);
            cuentas.put(c.getId(), c);
            return c;
        }

        public Optional<CuentaBancaria> obtenerCuenta(int id) {
            return Optional.ofNullable(cuentas.get(id));
        }

        public Collection<CuentaBancaria> listar() {
            return Collections.unmodifiableCollection(cuentas.values());
        }
    }

    /* ==============================
       === SERVICIOS ================
       ============================== */

    // Servicio para transferencias
    public static class ServicioTransferencias {
        public void transferir(CuentaBancaria origen, CuentaBancaria destino, double monto)
                throws CuentaBancaria.InsufficientFundsException {

            if (origen == null || destino == null)
                throw new IllegalArgumentException("Cuentas no válidas.");
            if (monto <= 0)
                throw new IllegalArgumentException("Monto debe ser mayor a cero.");

            synchronized (this) {
                origen.retirar(monto);
                destino.depositar(monto);
                origen.registrarTransaccion("Transferencia a ID:" + destino.getId(), -monto);
                destino.registrarTransaccion("Transferencia desde ID:" + origen.getId(), monto);
            }
        }
    }

    // Servicio para intereses o cargos
    public static class ServicioIntereses {
        public void aplicarIntereses(CuentaBancaria cuenta) {
            double tasa = (cuenta.getTipo() == CuentaBancaria.TipoCuenta.AHORROS) ? 0.02 : -0.01;
            double cambio = cuenta.getSaldo() * tasa;

            if (cambio > 0) cuenta.depositar(cambio);
            else {
                try { cuenta.retirar(Math.abs(cambio)); }
                catch (CuentaBancaria.InsufficientFundsException ignored) {}
            }

            cuenta.registrarTransaccion("Interés/Cargo aplicado", cambio);
        }
    }

    /* ==============================
       === APLICACIÓN PRINCIPAL =====
       ============================== */

    public static void main(String[] args) {
        Banco banco = new Banco();
        ServicioTransferencias transferService = new ServicioTransferencias();
        ServicioIntereses interesService = new ServicioIntereses();

        banco.crearCuenta("Alejo Fontecha", CuentaBancaria.TipoCuenta.CORRIENTE, 18.000);

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n========= BANCO =========");
                System.out.println("1 - Crear cuenta");
                System.out.println("2 - Consultar saldo");
                System.out.println("3 - Retirar");
                System.out.println("4 - Depositar");
                System.out.println("5 - Listar cuentas");
                System.out.println("6 - Transferir");
                System.out.println("7 - Ver historial");
                System.out.println("8 - Aplicar intereses/cargos");
                System.out.println("9 - Salir");
                System.out.print("Seleccione opción: ");

                String linea = sc.nextLine().trim();
                if (linea.isEmpty()) continue;

                int opcion;
                try { opcion = Integer.parseInt(linea); }
                catch (NumberFormatException e) { System.out.println("Opción inválida."); continue; }

                switch (opcion) {
                    case 1 -> crearCuentaFlow(sc, banco);
                    case 2 -> consultarSaldoFlow(sc, banco);
                    case 3 -> retirarFlow(sc, banco);
                    case 4 -> depositarFlow(sc, banco);
                    case 5 -> listarFlow(banco);
                    case 6 -> transferirFlow(sc, banco, transferService);
                    case 7 -> historialFlow(sc, banco);
                    case 8 -> aplicarInteresFlow(sc, banco, interesService);
                    case 9 -> { System.out.println("Saliendo..."); return; }
                    default -> System.out.println("Opción no válida.");
                }
            }
        }
    }

    /* ==============================
       === MÉTODOS DE FLUJO =========
       ============================== */

    private static void crearCuentaFlow(Scanner sc, Banco banco) {
        System.out.print("Nombre del titular: ");
        String nombre = sc.nextLine().trim();
        if (nombre.isEmpty()) { System.out.println("Nombre no puede estar vacío."); return; }

        System.out.print("Tipo (1=Corriente, 2=Ahorros): ");
        String t = sc.nextLine().trim();
        CuentaBancaria.TipoCuenta tipo = "2".equals(t)
                ? CuentaBancaria.TipoCuenta.AHORROS
                : CuentaBancaria.TipoCuenta.CORRIENTE;

        System.out.print("Saldo inicial: ");
        double saldoInicial;
        try { saldoInicial = Double.parseDouble(sc.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Saldo inválido."); return; }

        CuentaBancaria c = banco.crearCuenta(nombre, tipo, saldoInicial);
        System.out.println("Cuenta creada: " + c);
    }

    private static void consultarSaldoFlow(Scanner sc, Banco banco) {
        obtenerCuenta(sc, banco).ifPresentOrElse(
                c -> System.out.println("Saldo: " + c.getSaldo()),
                () -> System.out.println("Cuenta no encontrada."));
    }

    private static void retirarFlow(Scanner sc, Banco banco) {
        obtenerCuenta(sc, banco).ifPresentOrElse(c -> {
            System.out.print("Cantidad a retirar: ");
            try {
                double monto = Double.parseDouble(sc.nextLine().trim());
                c.retirar(monto);
                System.out.println("Retiro exitoso. Nuevo saldo: " + c.getSaldo());
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }, () -> System.out.println("Cuenta no encontrada."));
    }

    private static void depositarFlow(Scanner sc, Banco banco) {
        obtenerCuenta(sc, banco).ifPresentOrElse(c -> {
            System.out.print("Cantidad a depositar: ");
            try {
                double monto = Double.parseDouble(sc.nextLine().trim());
                c.depositar(monto);
                System.out.println("Depósito exitoso. Nuevo saldo: " + c.getSaldo());
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }, () -> System.out.println("Cuenta no encontrada."));
    }

    private static void listarFlow(Banco banco) {
        Collection<CuentaBancaria> cuentas = banco.listar();
        if (cuentas.isEmpty()) System.out.println("No hay cuentas registradas.");
        else cuentas.forEach(System.out::println);
    }

    private static void transferirFlow(Scanner sc, Banco banco, ServicioTransferencias servicio) {
        try {
            System.out.print("ID cuenta origen: ");
            int idOrigen = Integer.parseInt(sc.nextLine().trim());
            System.out.print("ID cuenta destino: ");
            int idDestino = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Monto a transferir: ");
            double monto = Double.parseDouble(sc.nextLine().trim());

            var origen = banco.obtenerCuenta(idOrigen);
            var destino = banco.obtenerCuenta(idDestino);

            if (origen.isEmpty() || destino.isEmpty()) {
                System.out.println("Alguna cuenta no existe.");
                return;
            }

            servicio.transferir(origen.get(), destino.get(), monto);
            System.out.println("Transferencia exitosa.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void historialFlow(Scanner sc, Banco banco) {
        obtenerCuenta(sc, banco).ifPresentOrElse(c -> {
            System.out.println("Historial de " + c.getCliente() + ":");
            c.getHistorial().forEach(System.out::println);
        }, () -> System.out.println("Cuenta no encontrada."));
    }

    private static void aplicarInteresFlow(Scanner sc, Banco banco, ServicioIntereses servicio) {
        obtenerCuenta(sc, banco).ifPresentOrElse(c -> {
            servicio.aplicarIntereses(c);
            System.out.println("Interés aplicado. Nuevo saldo: " + c.getSaldo());
        }, () -> System.out.println("Cuenta no encontrada."));
    }

    private static Optional<CuentaBancaria> obtenerCuenta(Scanner sc, Banco banco) {
        System.out.print("Ingrese ID de cuenta: ");
        try {
            int id = Integer.parseInt(sc.nextLine().trim());
            return banco.obtenerCuenta(id);
        } catch (NumberFormatException e) {
            System.out.println("ID inválido.");
            return Optional.empty();
        }
    }
}

