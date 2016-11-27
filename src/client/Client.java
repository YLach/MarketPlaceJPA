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
import java.util.StringTokenizer;

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
        register(MARKET_COMMAND), unregister(MARKET_COMMAND), sell(MARKET_COMMAND), buy(MARKET_COMMAND),
        wish(MARKET_COMMAND), list(MARKET_COMMAND), newAccount(BANK_COMMAND), deleteAccount(BANK_COMMAND),
        deposit(BANK_COMMAND), withdraw(BANK_COMMAND), balance(BANK_COMMAND), quit(APP_COMMAND), help(APP_COMMAND);

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
        try {
            this.account = bankobj.newAccount(getClientName());
        } catch (RejectedException e) {
            System.err.println("Account creation rejected : " + e);
            System.exit(1);
        }
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
        float amount = 0;
        int userInputTokenNo = 1;
        String itemName = null;
        float itemPrice = 0f;

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
                            itemName = tokenizer.nextToken();
                            break;
                        case 3:
                            try {
                                itemPrice = Float.parseFloat(tokenizer.nextToken());
                            } catch (NumberFormatException e) {
                                System.err.println("Illegal amount");
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
                command = new CommandMarket(commandName, new Item(itemName, itemPrice), this);
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

        public Item getItem() {
            return item;
        }

        private CommandMarket(CommandName commandName, Item item, Trader trader) {
            super(commandName);
            this.item = item;
            this.trader = trader;
        }

        @Override
        public void execute() throws RemoteException, bank.RejectedException, market.RejectedException {
            switch (this.getCommandName()) {
                case register:
                    market.register(clientName);
                    return;
                case unregister:
                    market.unregister(clientName);
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
                    System.out.println(market.getAllItems());
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
                    account = null;
                    return;
            }

            // all further commands require a Account reference
            switch (this.getCommandName()) {
                case deposit:
                    account.deposit(this.getAmount());
                    break;
                case withdraw:
                    account.withdraw(this.getAmount());
                    break;
                case balance:
                    System.out.println("balance: $" + account.getBalance());
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
