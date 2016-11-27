package market;


import bank.Account;
import bank.Bank;
import client.Trader;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class MarketServer extends UnicastRemoteObject implements Market {
    private static final String USAGE = "java market.MarketServer <LOCAL_REGISTRY_PORT_NUMBER>";
    private static final String BANK = "Nordea";
    private static final String DEFAULT_MARKET_NAME = "Market";
    private static final int DEFAULT_LOCAL_REGISTRY_PORT_NUMBER = 1099;

    private List<String> traders = new LinkedList<>();
    private AbstractMap<Item, Trader> items = new ConcurrentSkipListMap<>(); // Store Trader (and not their name) --> callback
    private AbstractMap<Item, Trader> wishList = new ConcurrentSkipListMap<>();
    private String bankname;
    Bank bankobj;

    /**
     * Constructor : to create the market remote object, we need to get first the
     * remote bank object
     * @param bankName
     * @param bankPort
     * @throws RemoteException
     */
    public MarketServer(String bankName, int bankPort) throws RemoteException {
        super(); // To export the servant class
        this.bankname = bankName;

        // We get the reference on the remote bank object
        try {
            Registry bankRegistry;
            try {
                bankRegistry = LocateRegistry.getRegistry(bankPort);
                bankRegistry.list();
            } catch (RemoteException e) {
                bankRegistry = LocateRegistry.createRegistry(bankPort);
            }
            bankobj = (Bank) bankRegistry.lookup(bankname);

        } catch (Exception e) {
            System.err.println("The runtime failed: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("Connected to bank: " + bankname);
    }

    @Override
    public synchronized void register(String traderName) throws RemoteException, RejectedException {
        if (traders.contains(traderName))
            throw new RejectedException("Trader " + traderName + " already registered");
        // Not already registered
        traders.add(traderName);
        System.out.println("Trader " + traderName + " registered on the market.");
    }

    @Override
    public synchronized void unregister(String traderName) throws RemoteException, RejectedException {
        // Remove all items belonging to that trader
        if (!traders.contains(traderName))
            throw new RejectedException("Trader " + traderName + " not registered");

        // TODO : Remove all objects and wishes from that trader ?
        // Remove all items belonging to this trader
        for(Map.Entry<Item, Trader> entry : items.entrySet()) {
            if (entry.getValue().getClientName().equals(traderName))
                items.remove(entry.getKey());
        }

        // Remove all wishes from this trader
        for(Map.Entry<Item, Trader> entry : wishList.entrySet()) {
            if (entry.getValue().getClientName().equals(traderName))
                wishList.remove(entry.getKey());
        }

        // Remove the trader from the market
        traders.remove(traderName);
        System.out.println("Trader " + traderName + " unregistered from the market.");
    }

    @Override
    public void sell(Item itemToSell, Trader trader) throws RemoteException, RejectedException {
        // Trader registered on the market ?
        if (!traders.contains(trader.getClientName()))
            throw new RejectedException("You are not registered on the market");

        // Item to sell already on the market ?
        if (items.containsKey(itemToSell))
            throw new RejectedException("Item " + itemToSell + " already on the market.");

        // Get an account ?
        Account account = bankobj.getAccount(trader.getClientName());
        if (account == null)
            throw new RejectedException("You cannot sell the item " + itemToSell  +
                    " : you do not get an account at bank " + bankname);

        // Yes
        items.put(itemToSell, trader);
        System.out.println(itemToSell + " puts on the market by " + trader.getClientName());

        //Debugging
        for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
            System.out.println("Wish from " + entry.getValue().getClientName() +
                    " : " + entry.getKey());
        }

        // Check if some buyers have placed a wish on that itemToSell
        for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
            //System.out.println("[DEBUG] " + itemToSell.compareTo(entry.getKey()));
            if ((itemToSell.compareTo(entry.getKey()) <= 0) &&
                    (entry.getKey().getName().equals(itemToSell.getName()))) {
                // Send callback
                entry.getValue().callback(itemToSell + " available on the market");

                // Remove its wish ?
                wishList.remove(entry.getKey());
            }
        }
    }

    @Override
    public void buy(Item itemToBuy, Trader trader) throws RemoteException, RejectedException,
            bank.RejectedException {
        // Trader registered on the market ?
        if (!traders.contains(trader.getClientName()))
            throw new RejectedException("You are not registered on the market");


        if (!items.containsKey(itemToBuy))
            throw new RejectedException("Item " + itemToBuy + " no longer on the market.");

        // Get an account ?
        Account accountBuyer = bankobj.getAccount(trader.getClientName());
        if (accountBuyer == null)
            throw new RejectedException("You cannot buy the item " + itemToBuy +
                    " : you do not get an account at bank " + bankname);

        // Enough money ?
        if (accountBuyer.getBalance() < itemToBuy.getPrice())
            throw new RejectedException("You cannot afford to buy this item : " + itemToBuy);

        // Yes
        Account accountSeller = bankobj.getAccount(items.get(itemToBuy).getClientName());
        accountBuyer.withdraw(itemToBuy.getPrice());
        accountSeller.deposit(itemToBuy.getPrice());
        Trader seller = items.remove(itemToBuy);
        seller.callback(itemToBuy + " has been sold");
        System.out.println(itemToBuy + " bought by " + trader.getClientName());
    }


    @Override
    public void wish(Item item, Trader trader) throws RemoteException, RejectedException,
            bank.RejectedException {
        // Trader registered on the market ?
        if (!traders.contains(trader.getClientName()))
            throw new RejectedException("You are not registered on the market");

        // Already did a wish for that item ?
        for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
            if ((entry.getKey().getName().equals(item.getName())) &&
                    (entry.getValue().equals(trader)))
                throw new RejectedException("You already placed a wish on " + item + " .");
        }

        // Someone else ?
        if (wishList.containsKey(item))
            throw new RejectedException("Someone else already placed the same wish on " + item + " .");


        wishList.put(item, trader);
        System.out.println("Wish from " + trader.getClientName() + " : " + item);

        /*System.out.println();
        for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
            System.out.println("Wish from " + entry.getValue().getClientName() + " : " + entry.getKey());
        }*/
    }

    @Override
    public String getAllItems() throws RemoteException {
        StringBuilder sb  = new StringBuilder();
        sb.append(" ------------------------------------\n");
        sb.append("|-------- ITEMS ON THE MARKET -------|\n");
        sb.append(" ------------------------------------\n\n");
        if (items.size() == 0)
            sb.append("No item available\n");
        for (Item i : items.keySet())
            sb.append(i.toString() + "\n");
        sb.append("-------------------------------------");
        //return items.keySet().toArray(new String[items.keySet().size()]);
        return sb.toString();
    }


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
            Naming.rebind("rmi://localhost:" + registryPortNumber + "/" + DEFAULT_MARKET_NAME,
                    new MarketServer(BANK, DEFAULT_LOCAL_REGISTRY_PORT_NUMBER));

        } catch (RemoteException | MalformedURLException re) {
            System.err.println(re);
            System.exit(1);
        }
    }
}
