package controller;
import model.*;
import networking.*;
import server.CEServer;
import view.ChatView;
import view.DocumentView;
import view.EditView;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLEditorKit;

import java.awt.*;
import java.awt.event.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

/**
 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
 * 
 * @Class This class deals with the client connecting to the server via a
 *        username and password (which is checked against registered users. Also
 *        sends/recieves packets when the user performs an action on the
 *        document
 * 
 */

public class CEController extends JFrame implements Serializable {
	/**
     *
     */

	private static final long serialVersionUID = 1L;

	private User mainUser;
	private ChatView chatView;
	private RevisionAssistant revAssist;
	private EditView editView;
	private Socket serversoc;
	private ObjectOutputStream outputStrm;
	private ObjectInputStream inputStrm;
	private int currentSelectedDoc;
	private DocumentView clientDocumentView;
	private Thread servertread;
	private Thread serverrevthread;
	private DocumentAssistant clientAllMaster;
	private boolean updateDocs;

	/**
	 * Constructor for CEController builds and calls all initializing methods
	 */
	public CEController() {
		currentSelectedDoc = 0;
		initUserModels();

	}
	/**
	 * Starts CEController at initial runtime
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		new CEController();
	}

	public void test() {

	}
	/**
	 * Creates user's model classes and instantiates them for uses
	 */
	private void initUserModels() {
		// Setting up hashing

		// Setting up the main data entry fields, un/pw/server stuff

		JTextField ipField = new JTextField();
		JTextField portField = new JTextField();
		JTextField nameField = new JTextField();
		JPasswordField passwordField = new JPasswordField();
		passwordField.setEchoChar('*');
		JPasswordField passwordConfirm = new JPasswordField();
		passwordConfirm.setEchoChar('*');
		String userName = "cat"; // TODO: Delete this+4 lines down
		String passWord = "meow";
		String serverAddress = "localhost";
		String port = "9001";
		String confirmedPW = "";
		// The popup asking for credentials
		// Also deals with getting the text and setting it
		Object[] message = {"Please Enter The Required Credentials\n\n", "Server:", ipField, "Port:", portField, "Username:", nameField, "Password:", passwordField, "Confirm Password\n(Or Fill Out To Create Account)", passwordConfirm};
		int option = JOptionPane.showConfirmDialog(this, message, "Enter all your values", JOptionPane.OK_CANCEL_OPTION);
		if (option == JOptionPane.OK_OPTION) {
			if (!ipField.getText().equals(""))
				if (!ipField.getText().equals(""))
					serverAddress = ipField.getText();
			if (!portField.getText().equals(""))
				port = portField.getText();
			if (!nameField.getText().equals(""))
				userName = nameField.getText();
			if (!(new String(passwordField.getPassword()).equals("")))
				passWord = new String(passwordField.getPassword());
			//
			confirmedPW = new String(passwordField.getPassword());
			if (!confirmedPW.equals("")) {
				// NewUserPacket newUSR = new NewUserPacket(userName, passWord);
				// if(!confirmedPW.equals(passWord)){
				//
				// JOptionPane.showMessageDialog(this,
				// "Passwords Do Not Match!");
				// }
			}
		}
		// Login packet based off of the previously fields
		byte[] passByte = null;

		try {
			MessageDigest hashSha = MessageDigest.getInstance("SHA-1");

			passByte = hashSha.digest(passWord.getBytes());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		LoginPacket lPK = new LoginPacket(userName, passByte);
		try {
			// Connects to server, gets Streams, and writes out the packet
			serversoc = new Socket(serverAddress, Integer.parseInt(port));
			outputStrm = new ObjectOutputStream(serversoc.getOutputStream());
			inputStrm = new ObjectInputStream(serversoc.getInputStream());
			outputStrm.writeObject(lPK);
			// Reads whether or not the login was a succcess/
			int toTest = (int) inputStrm.readObject();

			if (toTest == 1) {
				boolean endLoop = false;
				while (!endLoop) {
					JTextField unField = new JTextField();
					JPasswordField pwField = new JPasswordField();
					pwField.setEchoChar('*');
					Object[] recoverAcct = {"Invalid Password\n", "Please Enter Username And Desired New Password", unField, pwField};
					int option2 = JOptionPane.showConfirmDialog(this, recoverAcct, "ERROR", JOptionPane.OK_CANCEL_OPTION);

					if (option2 == JOptionPane.OK_OPTION) {
						RecoverPacket toPass = new RecoverPacket(unField.getText(), new String(pwField.getPassword()));
						outputStrm.writeObject(toPass);
						boolean recoverStatus = (boolean) inputStrm.readObject();
						if (recoverStatus) {
							JOptionPane.showMessageDialog(this, "Password Successfully Set, Logging In!!");
							endLoop = true;
						} else {
							JOptionPane.showMessageDialog(this, "Invalid Username\nPlease Try Again.");
						}
					}
					// TODO: If cancel?
				}
			}
			if (toTest == 2) {
				JOptionPane.showMessageDialog(this, "Account Doesn't Exist!\nClick 'OK' To Create Account With Previous Input");

				NewUserPacket newUser = new NewUserPacket(userName, passWord);
				outputStrm.writeObject(newUser);

			}

			mainUser = (User) inputStrm.readObject();
			setupGui();
			// Sets text field with default values and starts thread
			// Doc doc2Set = (Doc) inputStrm.readObject();
			// editView.setText(doc2Set.getDocContents());//new
			// Doc("Test Doc",12345, 1, null))
			ChatPacket temp = (ChatPacket) inputStrm.readObject();
			UpdateUserPacket usrPacket = (UpdateUserPacket) inputStrm.readObject();
			clientAllMaster = usrPacket.getML();
			if (clientAllMaster.getList().size() == 0) {
				updateDocs = false;
				// System.out.println("Initial value was false");
			} else {
				updateDocs = true;
				// System.out.println("Initial value was true");
			}

			List<String> toSet = temp.getChats();
			updateChat(toSet);
			new Thread(new DocSelectView(this)).start();

			serverrevthread = new Thread(new ServerRevisionWrite());
			servertread = new Thread(new ServerCommunicator());
			serverrevthread.start();
			servertread.start();

			// new Thread(new ServerRevisionWrite()).start();
			// new Thread(new ServerCommunicator()).start();

			// }
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Sets up le chat.

	}

	/**
	 * Setups the GUI for user to interact with
	 */
	private void setupGui() {
		// Permissions Pop up
		// Initializing graphic user interface variables
		JMenuBar menuBarCore = new JMenuBar();
		JMenu fileContainer = new JMenu("File");
		JMenu editContainer = new JMenu("Edit");
		JMenu userContainer = new JMenu("User");
		JMenu documentContainer = new JMenu("Doc Editor");
		JMenuItem save = new JMenuItem("Save");
		JMenuItem saveLocal = new JMenuItem("Save Local");
		JMenuItem quitOption = new JMenuItem("Quit");
		JMenuItem undo = new JMenuItem("Undo");
		JMenuItem redo = new JMenuItem("Redo");
		JMenuItem version = new JMenuItem("Version");
		JMenuItem addUser = new JMenuItem("Add User");
		JMenuItem changePW = new JMenuItem("Change Password");
		JMenuItem removeUser = new JMenuItem("Remove User");
		JMenuItem changePermission = new JMenuItem("Permissions Options");
		JMenuItem export = new JMenuItem("Export to HTML");

		this.setTitle("Collaborative Editor");
		// Adding action listeners for File
		quitOption.addActionListener(new ExitListener());
		save.addActionListener(new SaveListener());
		saveLocal.addActionListener(new SaveLocalListener());
		export.addActionListener(new ExportListener());
		// Adding Action Listeners for Edit
		undo.addActionListener(new UndoListener());
		redo.addActionListener(new RedoListener());
		version.addActionListener(new VersionListener());
		// Adding Action Listener for User
		changePW.addActionListener(new ChangePWListener());
		addUser.addActionListener(new AddUserListener());
		removeUser.addActionListener(new RemoveUserListener());
		changePermission.addActionListener(new PermissionListener());
		// Adding Action Listener for Doc Editor
		// add main menu buttons to bar
		menuBarCore.add(fileContainer);
		menuBarCore.add(editContainer);
		menuBarCore.add(userContainer);
		menuBarCore.add(documentContainer);
		// add document menu buttons
		JMenuItem newDoc = new JMenuItem("Create New Document");
		documentContainer.add(newDoc);
		newDoc.addActionListener(new NewDocumentListener());

		// fileContainer sub menu buttons
		fileContainer.add(save);
		fileContainer.add(saveLocal);
		fileContainer.add(export);
		fileContainer.add(quitOption);

		// editContainer sub menu buttons
		editContainer.add(undo);
		editContainer.add(redo);
		editContainer.add(version);
		// userContextBox.setText("<p style=\"color:red\">This is a paragraph.</p>");tainer
		// sub menu buttons
		userContainer.add(changePW);
		userContainer.add(addUser);
		userContainer.add(removeUser);
		userContainer.add(changePermission);
		// Add menu bar
		this.setJMenuBar(menuBarCore);
		// Add ChatView
		// RevisionAssistant
		chatView = new ChatView(mainUser, outputStrm, new RevisionAssistant());

		editView = new EditView(mainUser, new ArrayList<Doc>());
		// editView.setText("<p style=\"color:red\">This is a paragraph.</p>");
		this.setLayout(new BorderLayout());
		this.add(chatView, BorderLayout.EAST);
		this.add(editView, BorderLayout.CENTER);
		this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		this.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {
				CEController.this.selfExit();
			}
		});
		// pack and create!
		this.pack();
		this.setVisible(true);

	}
	/**
	 * updates chat view with current messages from Server
	 * 
	 * @param allMessages
	 *            All message from start of Server
	 */
	public void updateChat(List<String> allMessages) {

		chatView.updateChatPanel(allMessages);
	}
	/**
	 * Updates Revision view with current revisions from Server
	 * 
	 * @param rev
	 *            all revision from the document
	 */
	public void updateRevisionsView(RevisionAssistant rev) {

		chatView.UpdateRevisionPanel(rev);
	}
	/**
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts Exports
	 *         files to HTML Format for later use
	 */
	private class ExportListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {

			String comment = JOptionPane.showInputDialog("Enter a File Name To Save To As HTML");
			comment = comment + ".html";

			File toWrite = new File(comment);
			String toExport = editView.getText();
			ObjectOutputStream saveStream;
			try {
				saveStream = new ObjectOutputStream(new FileOutputStream(comment));
				saveStream.writeObject(toExport);
				JOptionPane.showMessageDialog(null, "File Successfully Saved!");
				saveStream.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
		

	

//			ArrayList<Doc> stuff = clientAllMaster.getList();
//			Doc toExport;
//			for (Doc theDoc : stuff) {
//				if (theDoc.getDocName().equals(comment)) {
//					toExport = theDoc;
//
//					ObjectOutputStream saveStream;
//					try {
//						saveStream = new ObjectOutputStream(new FileOutputStream(comment));
//						saveStream.writeObject(f);
//						JOptionPane.showMessageDialog(null, "File Successfully Saved!");
//						saveStream.close();
//					} catch (IOException e1) {
//						// TODO Auto-generated catch block
//						e1.printStackTrace();
//					}
//
//				}
//				else{
//					JOptionPane.showMessageDialog(null, "Invalid File!");
//				}
//			}

		}
	

	/**
	 * 
	 <<<<<<< Updated upstream
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class listens for Exit button to be clicked and starts closing methods
	 *        =======
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class ExitListener listens for Exit button to be clicked and starts
	 *        closing methods >>>>>>> Stashed changes
	 */
	// Listener Private Classes
	private class ExitListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			CEController.this.selfExit();
		}
	}
	/**
	 * 
	 <<<<<<< Updated upstream
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class Saves current document on server =======
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class SaveListener Saves current document on server >>>>>>> Stashed
	 *        changes
	 */
	private class SaveListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {

		}
	}
	/**
	 * <<<<<<< Updated upstream
	 * 
	 * @class listens for creation of new document
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 *         =======
	 * @class NewDocumentListener
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts listens
	 *         for creation of new document >>>>>>> Stashed changes
	 */
	private class NewDocumentListener implements ActionListener {

		private String name;

		public void actionPerformed(ActionEvent e) {
			name = JOptionPane.showInputDialog("What would you like your document to be called?");

			CreateNewDocument toSend = new CreateNewDocument(name, mainUser.getID());
			try {
				outputStrm.writeObject(toSend);
				// System.out.println("WroooooooteOut");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
	}
	/**
	 * <<<<<<< Updated upstream
	 * 
	 * @class Saves local copy of Document for further use
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts =======
	 * @class SaveLocalListener Saves local copy of Document for further use
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts >>>>>>>
	 *         Stashed changes
	 *
	 */
	private class SaveLocalListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}
	/**
	 * <<<<<<< Updated upstream
	 * 
	 * @class activates Undo
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts =======
	 * @class UndoListener activates Undo
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts >>>>>>>
	 *         Stashed changes
	 *
	 */
	private class UndoListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}
	/**
	 * @class Activates Redo
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 */
	private class RedoListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}

	/**
	 * @class Shows current version of the document
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 */

	/**
	 * @class VersionListener Shows current version of the document
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 */

	private class VersionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}
	/**
	 * <<<<<<< Updated upstream Add's User to document for editing
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 *         ======= Add's User to document for editing
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class AddUserListener >>>>>>> Stashed changes
	 */
	private class AddUserListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}
	/**
	 * Removes user from document for edition permision <<<<<<< Updated upstream
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 *         =======
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @Class RemoveUserListener >>>>>>> Stashed changes
	 */
	private class RemoveUserListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}

	/**
	 * opens permission window to set user's permissions for editting
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 */

	/**
	 * opens permission window to set user's permissions for editting
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class PermissionListener
	 */

	private class PermissionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}

	/**
	 * Change's Users PW
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 *
	 */

	/**
	 * Change's Users PW
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class ChangePWListener
	 */

	private class ChangePWListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
		}
	}

	// public void updateDocs() {
	//
	// GetDocsPacket toSend = new GetDocsPacket(mainUser);
	// try {
	// outputStrm.writeObject(toSend);
	// } catch (IOException e1) {
	// // TODO Auto-generated catch block
	// e1.printStackTrace();
	// }
	// }

	/**
	 * Logout is called when User is kicked from server
	 * 
	 */
	public void logout() {
		JOptionPane.showMessageDialog(this, "Server has Shutdown", "Server Quit", JOptionPane.ERROR_MESSAGE);
		System.exit(0);
	}
	/**
	 * self exit is used for safely disconnecting from the server
	 */
	public void selfExit() {
		try {
			LogoutPacket selfLog = new LogoutPacket();
			selfLog.setUser(mainUser.getUserName());
			outputStrm.writeObject(selfLog);
			servertread.stop();
			serverrevthread.stop();
			System.exit(0);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	/**
	 * Sets currentdocument to the selected one from the Doc window
	 * 
	 * @param toSet
	 *            Document number for selection
	 */
	public void setCurrentSelectedDoc(int toSet) {
		currentSelectedDoc = toSet;
		mainUser.setSelectedDoc(currentSelectedDoc);
		// System.out.println("Client says now we should care about" +
		// currentSelectedDoc);
		EditPacket needUpdates = new EditPacket(null, mainUser, currentSelectedDoc);
		try {
			outputStrm.writeObject(needUpdates);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * changes Document's name
	 */
	public void updateDocName() {
		editView.changeDoc(clientAllMaster.getList().get(currentSelectedDoc - 1).getDocName());
	}

	/* This Deals With Updating Out Docs! */
	/**
	 * open the Selection for documents doc and updates based on which document
	 * you wish to edit
	 * 
	 * @author NDBellisario
	 *
	 */
	private class DocSelectView implements Runnable {
		/**
		 * Constructs document view
		 * 
		 * @param arg
		 *            Controller you with to view from
		 */
		public DocSelectView(CEController arg) {
			clientDocumentView = new DocumentView(arg);

		}

		@Override
		/**
		 * runs runnable method to listen for new document that are created by other users 
		 */
		public void run() {
			while (true) {
				if (updateDocs) {
					// System.out.println("Inside update docs " + updateDocs);

					ArrayList<Doc> toPass = new ArrayList<Doc>();
					ArrayList<Doc> cheapLock = clientAllMaster.getList();
					// System.out.println(clientAllMaster.getList().size() +
					// "  "+ currentSelectedDoc);
					for (int i = 0; i < cheapLock.size(); i++) {
						// System.out.println("Running update on " +
						// cheapLock.size());
						if (cheapLock.get(i).canView((mainUser))) {
							toPass.add(cheapLock.get(i));
							// System.out.println("topass size " +
							// toPass.size());
						}
					}

					if (toPass.size() != 0) {
						clientDocumentView.updateList(toPass);
					}

					if (currentSelectedDoc != 0) {

						editView.changeDoc(clientAllMaster.getList().get(currentSelectedDoc - 1).getDocName());

					}

				}
				updateDocs = false;
				try {

					Thread.sleep(200);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Once connection is set up, this deals writing out updates
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @class ServerRevisionWrite
	 * */
	private class ServerRevisionWrite implements Runnable {
		/**
		 * runs infinite loop to listen for Server change to the document
		 * 
		 */
		public void run() {
			while (true) {

				if (currentSelectedDoc != 0) {
					try {
						// New edit packet and write it out!
						String toCompare = clientAllMaster.getList().get(currentSelectedDoc - 1).getDocContents();
						if (!(editView.getText().equals(toCompare))) {
							EditPacket newTimedRevision = new EditPacket(editView, mainUser, currentSelectedDoc);
							outputStrm.writeObject(newTimedRevision);
						}
						// System.out.println("Wrote a Packet! of Doc Type " +
						// currentSelectedDoc);
						outputStrm.reset();
						Thread.sleep(2000); // Only want to send every 2000 ms.

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					try {
						// System.out.println("Didnt Write a Packet!");
						Thread.sleep(700);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}
		}
	}

	/**
	 * This class will get new revisions and update GUI
	 * 
	 * @author Nicholas,Taylor,Omri,Eric,Cameron Team Amphetamine Salts
	 * @Class ServerCommunicator
	 * */
	private class ServerCommunicator implements Runnable {
		@Override
		public void run() {
			while (true) {
				try {
					// Sets text to the ReadIn
					Object unknown = inputStrm.readObject();
					// System.out.println("Read Something");
					if ((unknown instanceof EditPacket)) {

						EditPacket newPacket = (EditPacket) unknown;
						clientAllMaster = newPacket.getMaster();

						if (newPacket.getDoc().canView(mainUser)) {
							for (Doc curr : clientDocumentView.getDocs()) {

								if (newPacket.getDocID() != curr.getDocIdentification()) {
									updateDocs = true;

								}
							}
							if (clientDocumentView.getDocs().size() == 0) {
								updateDocs = true;
							}

						}

						if (((newPacket.getDocID() == currentSelectedDoc) && newPacket.getDocID() != 0)) {
							editView.setText(clientAllMaster.getList().get(currentSelectedDoc - 1).getDocContents(), newPacket.getStyle());
						}

						// System.out.println("The packet we just got " +
						// updateDocs);

					} else if (unknown instanceof ChatPacket) {
						@SuppressWarnings("unchecked")
						List<String> toSet = (ArrayList<String>) ((ChatPacket) unknown).getChats();
						updateChat(toSet);

					} else if (unknown instanceof LogoutPacket) {

						LogoutPacket log = (LogoutPacket) unknown;
						log.execute(CEController.this);
					} else if (unknown instanceof CreateNewDocument) {
						CreateNewDocument thePacket = (CreateNewDocument) unknown;
						clientAllMaster = thePacket.getDocAs();
						currentSelectedDoc = thePacket.getDocID();
						updateDocs = true;

					} else if (unknown instanceof UpdateUserPacket) {
						UpdateUserPacket usrPacket = (UpdateUserPacket) unknown;
						clientAllMaster = usrPacket.getML();
						if (clientAllMaster.getList().size() == 0)
							updateDocs = false;
						else
							updateDocs = true;
					}
				} catch (ClassNotFoundException | IOException e) {
					// TODO Auto-generated catch block
					System.out.println("Through An Exception");

				}
			}
		}
	}

}
