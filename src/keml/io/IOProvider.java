/**
 * 
 */
package keml.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import keml.Conversation;
import keml.io.graphml.GraphML2KEML;
import keml.io.llm.ChatGPTReader;
import keml.io.llm.ConversationAdder;

/**
 * 
 */
public class IOProvider {

	String defaultFolder;
	String folder;

	IOProvider(String defaultFolder) {
		this.defaultFolder = defaultFolder;
	}

	private void runIOProvider() {
		folder = "";
		FileBrowseDialog fb = new FileBrowseDialog(this);
		new Thread(fb).start();
		boolean foundFolder = false;
		File resultsFolder = null;
		String conversationFolder = "";
		File files[] = null;
		while (!foundFolder) {
			synchronized (this) {
				try {
					while (folder.isEmpty()) {
						this.wait();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			resultsFolder = new File(folder + "/keml/");
			System.out.println("You started the graphml to keml conversion.\n I will read graphml files from " + folder
					+ ".\n I will write the resulting files into " + resultsFolder);

			String conversations = folder + "/conversations.json";
			conversationFolder = folder + "/conv/";
			try {
				new ChatGPTReader().split(conversations, conversationFolder);
				files = new File(folder + "/graphml/")
						.listFiles((dir, name) -> name.toLowerCase().endsWith(".graphml"));
				if (files == null) {
					throw new NullPointerException();
				}
				foundFolder = true;
				fb.dispose();
			} catch (IOException e) {
				fb.message.setText(
						"<html><body>Cannot split<br>" + conversations + "<br>Choose another folder</body></html>");
				folder = "";
				fb.setVisible(true);
			} catch (NullPointerException e) {
				fb.message.setText("<html><body>No .graphml files found in given folder<br>" + folder
						+ "<br>Choose another folder</body></html>");
				folder = "";
				fb.setVisible(true);
			}
		}

		KemlFileHandler fileHandler = new KemlFileHandler();

		for (File file : files) {
			try {
				transformFile(file, fileHandler, resultsFolder, getConvFileFromFile(file, conversationFolder));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void transformFile(File graphmlPath, KemlFileHandler fileHandler, File targetFolder,
			File originalConv) throws Exception {
		Conversation conv = new GraphML2KEML().readFromPath(graphmlPath.getAbsolutePath());
		ConversationAdder.addOriginalConv(conv, originalConv);
		String kemlBasePath = targetFolder + "/" + FilenameUtils.removeExtension(graphmlPath.getName());
		String kemlPath = kemlBasePath + ".keml";
		fileHandler.saveKeml(conv, kemlPath);
		System.out.println("Saved file as " + kemlPath);
		String kemlJSONPath = kemlBasePath + "-keml.json";
		fileHandler.saveKemlJSON(conv, kemlJSONPath);
		System.out.println("Saved file as " + kemlJSONPath);
	}

	private static File getConvFileFromFile(File file, String conversationFolder) {
		return new File(conversationFolder + FilenameUtils.getBaseName(file.getPath()) + ".json");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String defaultPath = "../keml.sample/introductoryExamples";
		IOProvider ioProvider = new IOProvider(defaultPath);
		ioProvider.runIOProvider();
	}

}
