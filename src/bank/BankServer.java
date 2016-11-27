package bank;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.security.AccessControlContext;
import java.security.AccessControlException;

public class BankServer {
    private static final String USAGE = "java bank.BankServer <bank_rmi_url> <port>";
    private static final String BANK = "Nordea";
    private static final int DEFAULT_LOCAL_REGISTRY_PORT_NUMBER = 1099;

    public BankServer(String bankName, int port) {
        try {
            Bank bankobj = new BankImpl(bankName);
            // Register the newly created object at rmiregistry.
            try {
                LocateRegistry.getRegistry(port).list();
            } catch (AccessControlException | RemoteException e) {
                LocateRegistry.createRegistry(port);
            }
            Naming.rebind(bankName, bankobj);
            System.out.println(bankobj + " is ready.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length > 2 || (args.length > 0 && args[0].equalsIgnoreCase("-h"))) {
            System.out.println(USAGE);
            System.exit(1);
        }

        //System.setSecurityManager(new SecurityManager());

        String bankName = BANK;
        int registryPortNumber = DEFAULT_LOCAL_REGISTRY_PORT_NUMBER;
        if (args.length > 1) {
            bankName = args[0];
            try {
                if (args.length > 1) {
                    registryPortNumber = Integer.parseInt(args[1]);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number for the registry");
                System.exit(1);
            }
        }
        new BankServer(bankName, registryPortNumber);
    }
}
