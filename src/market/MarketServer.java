package market;

import client.Client;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;


public class MarketServer {
    private static final String USAGE = "java market.MarketImpl <LOCAL_REGISTRY_PORT_NUMBER>";
    private static final int DEFAULT_LOCAL_REGISTRY_PORT_NUMBER = 1099;
    private static final String DEFAULT_MARKET_NAME = "Market";
    private static final String BANK = "Nordea";

    public static void main(String[] args) {
        if (args.length > 1 || (args.length > 0 && args[0].equalsIgnoreCase("-h"))) {
            System.out.println(USAGE);
            System.exit(1);
        }

        // Parse args to get the registry port number
        int registryPortNumber = DEFAULT_LOCAL_REGISTRY_PORT_NUMBER;
        try {
            if (args.length > 0) {
                registryPortNumber = Integer.parseInt(args[0]);
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number for the registry");
            System.exit(1);
        }

        try {
            try {
                LocateRegistry.getRegistry(registryPortNumber).list();
            } catch (RemoteException e) {
                LocateRegistry.createRegistry(registryPortNumber);
            }

            // Bind the market in the RMIRegistry
            MarketImpl market = new MarketImpl(BANK, DEFAULT_LOCAL_REGISTRY_PORT_NUMBER);
            Naming.rebind("rmi://localhost:" + registryPortNumber + "/" + DEFAULT_MARKET_NAME, market);

        } catch (RemoteException | MalformedURLException re) {
            System.err.println(re);
            System.exit(1);
        }
    }
}
