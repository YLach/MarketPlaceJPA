package client;


import bank.Account;
import bank.Bank;
import bank.RejectedException;
import market.Item;
import market.Market;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

public class Client extends UnicastRemoteObject implements Trader {
    private static final String USAGE = "java market.Client <CLIENT_NAME> <REGISTRY_PORT_NUMBER>";
    private static final String DEFAULT_BANK = "Nordea";
    private static final String DEFAULT_MARKET = "Market";
    private static final int DEFAULT_REMOTE_REGISTRY_PORT_NUMBER = 1099;
    private static final int DEFAULT_LOCAL_REGISTRY_PORT_NUMBER = 2000;
    private static final int APP_COMMAND = 1;
    private static final int BANK_COMMAND = 2;
    private static final int MARKET_COMMAND = 3;


    private String clientName;
    private String marketName;
    Market market;
    private String bankName;
    Bank bankobj;
    Account account;

    // Enumeration of possible commands
    enum CommandName {
        register(MARKET_COMMAND), unregister(MARKET_COMMAND), login(MARKET_COMMAND),
        logout(MARKET_COMMAND), stats(MARKET_COMMAND), sell(MARKET_COMMAND), buy(MARKET_COMMAND), wish(MARKET_COMMAND),
        list(MARKET_COMMAND), newAccount(BANK_COMMAND), deleteAccount(BANK_COMMAND),
        deposit(BANK_COMMAND), withdraw(BANK_COMMAND), balance(BANK_COMMAND),
        quit(APP_COMMAND), help(APP_COMMAND);

        private int type;
        public int getType() {
            return this.type;
        }
        CommandName(int type) {
            this.type = type;
        }
    }

    /**
     * Constructor
     * @param clientName
     * @throws RemoteException
     */
    public Client(String clientName) throws RemoteException {
        this(clientName, DEFAULT_MARKET, DEFAULT_BANK);
    }

    /**
     * Constructor
     * @param clientName
     * @param marketName
     * @param bankName
     * @throws RemoteException
     */
    public Client(String clientName, String marketName, String bankName) throws RemoteException {
        super(); // Exportation in RMI Runtime
        this.clientName = clientName;
        this.marketName = marketName;
        this.bankName = bankName;

        // Get reference to the Market and the Bank
        try {
            Registry remoteRegistry;
            try {
                remoteRegistry = LocateRegistry.getRegistry(DEFAULT_REMOTE_REGISTRY_PORT_NUMBER);
            } catch (RemoteException e) {
                remoteRegistry = LocateRegistry.createRegistry(DEFAULT_REMOTE_REGISTRY_PORT_NUMBER);
            }
            bankobj = (Bank) remoteRegistry.lookup(bankName);
            market = (Market) remoteRegistry.lookup(marketName);

        } catch (Exception e) {
            System.err.println("The runtime failed: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("Connected to bank: " + this.bankName);
        System.out.println("Connected to market: " + this.marketName);

        // New bank account
       /* try {
            this.account = bankobj.newAccount(getClientName());
        } catch (RejectedException e) {
            System.err.println("Account creation rejected : " + e);
            System.exit(1);
        }*/
    }

    @Override
    public void callback(String message) throws RemoteException {
        // Just display the callback message
        System.out.println("[CALLBACK] " + message);
    }


    // Getters and setters
    public String getClientName() {
        return clientName;
    }

    public Market getMarket() {
        return market;
    }

    public Bank getBankobj() {
        return bankobj;
    }

    public Account getAccount() {
        return account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Client client = (Client) o;

        return clientName.equals(client.clientName);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + clientName.hashCode();
        return result;
    }

    // Console application
    public void run() {

        BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print(this.clientName + "@" + this.marketName + ">");
            try {
                String userInput = consoleIn.readLine();
                Command command = parse(userInput);
                if (command != null)
                    command.execute();
            } catch (market.RejectedException | bank.RejectedException re) {
                System.err.println(re);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Command parse(String userInput) {
        if (userInput == null) {
            return null;
        }

        StringTokenizer tokenizer = new StringTokenizer(userInput);
        if (tokenizer.countTokens() == 0) {
            return null;
        }

        CommandName commandName = null;
        String password = "";

        String itemName = null;
        float itemPrice = 0f;
        int itemAmount = 1;

        float amount = 0; // Bank amout
        int userInputTokenNo = 1;

        // Parse the command
        try {
            String commandNameString = tokenizer.nextToken();
            commandName = CommandName.valueOf(CommandName.class, commandNameString);
            //System.out.println("[DEBUG] Command name : " + commandName);
            userInputTokenNo++;
        } catch (IllegalArgumentException commandDoesNotExist) {
            System.err.println("Illegal command name : " + commandName);
            return null;
        }

        while (tokenizer.hasMoreTokens()) {
            switch (commandName.getType()) {
                case APP_COMMAND:
                    System.err.println("Illegal app command : too much parameters");
                    return null;
                case BANK_COMMAND:
                    if (userInputTokenNo > 2) {
                        System.err.println("Illegal bank command");
                        return null;
                    }

                    try {
                        amount = Float.parseFloat(tokenizer.nextToken());
                    } catch (NumberFormatException e) {
                        System.err.println("Illegal amount");
                        return null;
                    }
                    break;
                case MARKET_COMMAND:
                    switch (userInputTokenNo) {
                        case 2:
                            if (commandName.equals(CommandName.logout) || commandName.equals(CommandName.list) ||
                             commandName.equals(CommandName.stats)) {
                                System.err.println("Illegal number of arguments");
                                return null;
                            }

                            if (commandName.equals(CommandName.register) || commandName.equals(CommandName.login))
                                password = tokenizer.nextToken();
                            else
                                // Buy/Sell item commands
                                itemName = tokenizer.nextToken();
                            break;
                        case 3:
                            if (!(commandName.equals(CommandName.buy) || commandName.equals(CommandName.sell) ||
                                    commandName.equals(CommandName.wish))) {
                                System.err.println("Illegal number of arguments");
                                return null;
                            }

                            try {
                                itemPrice = Float.parseFloat(tokenizer.nextToken());
                            } catch (NumberFormatException e) {
                                System.err.println("Illegal price");
                                return null;
                            }
                            break;
                        case 4:
                            if (!(commandName.equals(CommandName.buy) || commandName.equals(CommandName.sell))) {
                                System.err.println("Illegal number of arguments");
                                return null;
                            }

                            try {
                                itemAmount = Integer.parseInt(tokenizer.nextToken());
                            } catch (NumberFormatException e) {
                                System.err.println("Illegal amount of items");
                                return null;
                            }
                            break;
                        default:
                            System.err.println("Illegal market command");
                            return null;
                    }
                    break;
                default:
                    System.err.println("Illegal command name : " + commandName);
                    return null;
            }
            userInputTokenNo++;
        }

        Command command;
        switch (commandName.getType()) {
            case APP_COMMAND:
                command = new Command(commandName);
                break;
            case MARKET_COMMAND:
                if ((commandName.equals(CommandName.sell) || commandName.equals(CommandName.buy) ||
                        commandName.equals(CommandName.wish)) && (itemName == null)) {
                    System.err.println("You need to specify the item name");
                    return null;
                }

                if (commandName.equals(CommandName.register) || commandName.equals(CommandName.login))
                    command = new CommandMarket(commandName, this, password);
                else
                    command = new CommandMarket(commandName, new Item(itemName, itemPrice, itemAmount), this);
                break;
            case BANK_COMMAND:
                command = new CommandBank(commandName, this.clientName, amount);
                break;
            default:
                System.err.println("Illegal command");
                return null;
        }
        return command;
    }


    private class Command {
        protected CommandName commandName;

        private Command(Client.CommandName commandName) {
            this.commandName = commandName;
        }

        protected CommandName getCommandName() {
            return commandName;
        }

        public void execute() throws RemoteException, bank.RejectedException, market.RejectedException {
            switch (this.getCommandName()) {
                case quit:
                    System.exit(0);
                case help:
                    for (CommandName commandName : CommandName.values()) {
                        System.out.println(commandName);
                    }
                    return;
                default:
                    System.err.println("Illegal app command to be executed");
            }
        }
    }

    private class CommandMarket extends Command {
        private Item item;
        private Trader trader;
        private String password;

        public Item getItem() {
            return item;
        }

        private CommandMarket(CommandName commandName, Item item, Trader trader) {
            super(commandName);
            this.item = item;
            this.trader = trader;
        }

        private CommandMarket(CommandName commandName, Trader trader, String password) {
            super(commandName);
            this.trader = trader;
            this.password = password;
        }

        @Override
        public void execute() throws RemoteException, bank.RejectedException, market.RejectedException {
            switch (this.getCommandName()) {
                case login:
                    market.login(trader, password);
                    return;
                case logout:
                    market.logout(clientName);
                    return;
                case register:
                    market.register(trader, password);
                    return;
                case unregister:
                    market.unregister(trader);
                    return;
                case stats:
                    ArrayList<String> stats = market.getStats(clientName);
                    StringBuilder sb  = new StringBuilder();
                    sb.append(" ------------------------------------\n");
                    sb.append("|----------- MY STATISTICS ----------|\n");
                    sb.append(" ------------------------------------\n");
                    sb.append("  Username :" + clientName + "\n");
                    sb.append("  Total nb of items bought: " + stats.get(Market.INDEX_NB_TOTAL_ITEMS_BOUGHT) + "\n");
                    sb.append("  Total nb of items sold: " + stats.get(Market.INDEX_NB_TOTAL_ITEMS_SOLD) + "\n");
                    sb.append("-------------------------------------");
                    System.out.println(sb.toString());
                    return;
                case buy:
                    market.buy(this.item, this.trader);
                    return;
                case sell:
                    market.sell(this.item, this.trader);
                    return;
                case wish:
                    market.wish(this.item, this.trader);
                    return;
                case list:
                    ArrayList<Item> items = market.getAllItems();
                    StringBuilder sl  = new StringBuilder();
                    sl.append(" ------------------------------------\n");
                    sl.append("|-------- ITEMS ON THE MARKET -------|\n");
                    sl.append(" ------------------------------------\n\n");
                    if (items.size() == 0)
                        sl.append("No item available\n");
                    else {
                        for (Item i : items)
                            sl.append(i.toString() + "\n");
                    }
                    sl.append("-------------------------------------");
                    System.out.println(sl.toString());
                    return;
                default:
                    System.err.println("Illegal market command to be executed");
            }
        }
    }

    private class CommandBank extends Command {
        private String userName;
        private float amount;

        private String getUserName() {
            return userName;
        }

        private float getAmount() {
            return amount;
        }

        private CommandBank(Client.CommandName commandName, String userName, float amount) {
            super(commandName);
            this.userName = userName;
            this.amount = amount;
        }

        @Override
        public void execute() throws RemoteException, bank.RejectedException, market.RejectedException {
            // all further commands require a name to be specified
            switch (this.getCommandName()) {
                case newAccount:
                    account = bankobj.newAccount(clientName);
                    return;
                case deleteAccount:
                    bankobj.deleteAccount(clientName);
                    return;
            }

            // all further commands require a Account reference
            switch (this.getCommandName()) {
                case deposit:
                    bankobj.deposit(clientName, amount);
                    break;
                case withdraw:
                    bankobj.withdraw(clientName, amount);
                    break;
                case balance:
                    System.out.println("balance: $" + bankobj.findAccount(clientName).getBalance());
                    break;
                default:
                    System.err.println("Illegal bank command to be executed");
            }
        }
    }


    // MAIN
    public static void main(String[] args) {
        if (args.length > 2 || (args.length > 0 && args[0].equalsIgnoreCase("-h"))) {
            System.out.println(USAGE);
            System.exit(1);
        }

        int localRegistryPortNumber = DEFAULT_LOCAL_REGISTRY_PORT_NUMBER;
        String clientName = "";
        try {
            if (args.length == 2) {
                clientName = args[0];
                localRegistryPortNumber = Integer.parseInt(args[1]);
                System.out.println("[DEBUG] Client : " + clientName + " | Port : " + localRegistryPortNumber); // TODO To remove
            } else {
                System.out.println(USAGE);
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number for the RMI registry");
            System.exit(1);
        }

        try {
            try {
                LocateRegistry.getRegistry(localRegistryPortNumber);
            } catch (RemoteException e) {
                LocateRegistry.createRegistry(localRegistryPortNumber);
            }
            Client client = new Client(clientName);
            Naming.rebind(clientName, client);

            client.run();
        } catch (Exception e) {
            System.err.println("The runtime failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
