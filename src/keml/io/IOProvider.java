package keml.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import keml.Conversation;
import keml.io.graphml.GraphML2KEML;
import keml.io.llm.ChatGPTReader;
import keml.io.llm.ConversationAdder;

public class IOProvider {

	String defaultFolder;
	String folder;

	FileBrowseDialog fb;

	public IOProvider(String defaultFolder) {
		this.defaultFolder = defaultFolder;
	}

	private void runIOProvider() {
		this.fb = new FileBrowseDialog(this);
	}

	public void tryChosenPath() {
		String conversations = folder + "/conversations.json";
		String conversationFolder = folder + "/conv/";
		File resultsFolder = new File(folder + "/keml/");
		KemlFileHandler fileHandler = new KemlFileHandler();
		try {
			new ChatGPTReader().split(conversations, conversationFolder);
		} catch (IOException e) {
			System.err.println("Cannot split " + conversations);
			System.err.println(e);
		}
		File[] files = new File(folder + "/graphml/").listFiles((dir, name) -> name.toLowerCase().endsWith(".graphml"));
		if (files == null) {
			fb.message.setText("<html><body>No /graphml folder found in given folder<br>" + folder
					+ "<br>Choose another folder</body></html>");
			fb.setVisible(true);
		} else if (files.length == 0) {
			fb.message.setText("<html><body>No .graphml files found in given folder<br>" + folder + "/graphml/"
					+ "<br>Choose another folder</body></html>");
			fb.setVisible(true);
		} else {
			fb.dispose();
			for (File file : files) {
				try {
					transformFile(file, fileHandler, resultsFolder, getConvFileFromFile(file, conversationFolder));
				} catch (Exception e) {
					e.printStackTrace();
				}
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

	public static void main(String[] args) {
		String defaultPath = "../keml.sample/introductoryExamples";
		IOProvider io = new IOProvider(defaultPath);
		io.runIOProvider();
	}

}
