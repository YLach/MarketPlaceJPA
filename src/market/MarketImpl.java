package market;


import bank.Account;
import bank.Bank;
import client.Trader;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class MarketImpl extends UnicastRemoteObject implements Market {
    private static final String BANK = "Nordea";
    private static final int DEFAULT_LOCAL_REGISTRY_PORT_NUMBER = 1099;

    private List<String> loggedIn = new LinkedList<>();
    private AbstractMap<Item, Trader> wishList = new ConcurrentSkipListMap<>();
    private String bankname;
    Bank bankobj;

    private EntityManagerFactory emFactory;

    /**
     * Default constructor
     * @throws RemoteException
     */
    public MarketImpl() throws RemoteException {
        this(BANK, DEFAULT_LOCAL_REGISTRY_PORT_NUMBER);
    }

    /**
     * Constructor : to create the market remote object, we need to get first the
     * remote bank object
     * @param bankName
     * @param bankPort
     * @throws RemoteException
     */
    public MarketImpl(String bankName, int bankPort) throws RemoteException {
        super(); // To export the servant class

        // Create the Entity Manage Factory used to create the Entity Manager
        emFactory = Persistence.createEntityManagerFactory("market");

        // We get the reference on the remote bank object
        this.bankname = bankName;
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
    public void login(String username, String password) throws RemoteException, RejectedException {

        if (loggedIn.contains(username))
            throw new RejectedException("You are already logged in");

        EntityManager em = null;

        try {
            em = beginTransaction();

            // Check if user with the same name already exists
            User existingUser = em.find(User.class, username);
            if (existingUser == null)
                throw new RejectedException("Login failed: you are not registered on the market.");

            if (!existingUser.getPassword().equals(password))
                throw new RejectedException("Login failed: wrong password");

            loggedIn.add(username);
            System.out.println("Trader " + username + " logged in on the market.");
        } finally {
            if (em != null)
                commitTransaction(em);
        }

    }

    @Override
    public synchronized void register(String traderName, String password)
            throws RemoteException, RejectedException {
        EntityManager em = null;
        User user;
        try {
            em = beginTransaction();

            // Check if user with the same name already exists
            User existingUser = em.find(User.class, traderName);
            if (existingUser != null)
                throw new RejectedException("Trader " + traderName + " already registered");

            // Not already registered
            // Check the password length
            if (password.length() < 8)
                throw new RejectedException("Invalid size of password : must contain at least 8 characters");

            // Register the new user
            user = new User(traderName, password);
            em.persist(user);

            System.out.println("Trader " + traderName + " registered on the market.");

        } finally {
            if (em != null)
                commitTransaction(em);
        }

    }

    @Override
    public synchronized void unregister(String traderName) throws RemoteException, RejectedException {
        // Remove all items belonging to that trader
        if (!loggedIn.contains(traderName))
            throw new RejectedException("Trader " + traderName + " not registered");

        EntityManager em = null;

        try {
            em = beginTransaction();

            // Remove all items belonging to this trader
            List<Item> itemsSeller = em.createNamedQuery("FindItemsBySeller", Item.class).
                    setParameter("sellerName", traderName).getResultList();

            for (Item i : itemsSeller) {
                em.remove(i);
            }

            // Remove all wishes from this trader
            for(Map.Entry<Item, Trader> entry : wishList.entrySet()) {
                if (entry.getValue().getClientName().equals(traderName))
                    wishList.remove(entry.getKey());
            }

        } finally {
            if (em != null)
                commitTransaction(em);

            // Remove the trader from the market
            loggedIn.remove(traderName);
            System.out.println("Trader " + traderName + " unregistered from the market.");
        }
    }

    @Override
    public void sell(Item itemToSell, Trader trader) throws RemoteException, RejectedException {

        // Trader registered on the market ?
        if (!loggedIn.contains(trader.getClientName()))
            throw new RejectedException("You are not registered on the market");

        // Get an account ?
        Account account = bankobj.findAccount(trader.getClientName());
        if (account == null)
            throw new RejectedException("You cannot sell the item " + itemToSell  +
                    " : you do not get an account at bank " + bankname);

        EntityManager em = null;

        // Item to sell already on the market ?
        try {
            em = beginTransaction();

            Item item = em.find(Item.class, new ItemKey(itemToSell.getName(), itemToSell.getPrice()));

            if ( (item != null) && (!item.getSeller().getUsername().equals(trader.getClientName())) )
                throw new RejectedException("Item " + itemToSell + " already on the market.");

            // Can be sold
            if (item != null)
                item.setAmount(item.getAmount() + itemToSell.getAmount());
            else {
                User seller = em.find(User.class, trader.getClientName());

                // Store a new Item persistently
                em.persist(new Item(itemToSell, seller));
            }

        } finally {
            if (em != null) {
                commitTransaction(em);

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
        }
    }

    @Override
    public void buy(Item itemToBuy, Trader trader) throws RemoteException, RejectedException,
            bank.RejectedException {
        // Trader registered on the market ?
        if (!loggedIn.contains(trader.getClientName()))
            throw new RejectedException("You are not registered on the market");

        EntityManager em = null;

        try {
            em = beginTransaction();

            // Item on the market ?
            Item itemToSell = em.find(Item.class, new ItemKey(itemToBuy.getName(), itemToBuy.getPrice()));

            if (itemToSell == null)
                throw new RejectedException("Item " + itemToBuy + " no longer on the market.");

            // Enough amount of item ?
            if (itemToSell.getAmount() < itemToBuy.getAmount())
                throw new RejectedException("You cannot buy " + itemToBuy.getAmount() + " items " + itemToBuy +
                        " : there is only " + itemToSell.getAmount() + " items remaining on the market");

            // Get an account ?
            Account accountBuyer = bankobj.findAccount(trader.getClientName());
            if (accountBuyer == null)
                throw new RejectedException("You cannot buy the item " + itemToBuy +
                        " : you do not get an account at bank " + bankname);

            // Enough money ?

            if (accountBuyer.getBalance() < (itemToSell.getPrice() * itemToBuy.getAmount()))
                throw new RejectedException("You cannot afford to buy " + itemToBuy.getAmount() + " " + itemToBuy);

            // Yes
            Account accountSeller = bankobj.findAccount(itemToSell.getSeller().getUsername());
            accountBuyer.withdraw(itemToSell.getPrice() * itemToBuy.getAmount());
            accountSeller.deposit(itemToSell.getPrice() * itemToBuy.getAmount());

            if (itemToSell.getAmount() == itemToBuy.getAmount()) {
                // Remove item from database
                em.remove(itemToSell);
            } else {
                itemToSell.setAmount(itemToSell.getAmount() - itemToBuy.getAmount());
            }

            // Callback   TODO
            //  seller.callback(itemName + " has been sold");

        } finally {
            if (em != null) {
                commitTransaction(em);

                System.out.println(itemToBuy + " bought by " + trader.getClientName());
            }
        }
    }


    @Override
    public void wish(Item item, Trader trader) throws RemoteException, RejectedException,
            bank.RejectedException {
        // Trader registered on the market ?
        if (!loggedIn.contains(trader.getClientName()))
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

        EntityManager em = null;

        try {
            em = beginTransaction();

            List<Item> items = em.createNamedQuery("AllItemsToSell", Item.class).getResultList();
            if (items.size() == 0)
                sb.append("No item available\n");
            else {
                for (Item i : items)
                    sb.append(i.toString() + "\n");
            }

        } finally {
            commitTransaction(em);
        }

        sb.append("-------------------------------------");
        return sb.toString();
    }


    private EntityManager beginTransaction()
    {
        EntityManager em = emFactory.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        return em;
    }

    private void commitTransaction(EntityManager em)
    {
        em.getTransaction().commit();
    }

}
