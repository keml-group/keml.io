package keml.io;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JTextField;

public class FileBrowseDialog extends JFrame implements ActionListener, Runnable {

	JButton folderSelecterButton;
	JButton folderOpenerButton;
	JTextField textField;

	IOProvider ioProvider;

	String last = "utils/paths/last_used_paths.txt";

	FileBrowseDialog(IOProvider ioProvider) {
		this.ioProvider = ioProvider;
	}

	private void buildFrame() {
		this.setTitle("Choose conversation files");
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLayout(new FlowLayout());
		ImageIcon logo = new ImageIcon("utils/logos/camel.png");
		this.setIconImage(logo.getImage());
		this.setLocationRelativeTo(null);

		folderSelecterButton = new JButton("Select folder");
		folderSelecterButton.addActionListener(this);
		this.add(folderSelecterButton);

		folderOpenerButton = new JButton("Open folder >>");
		folderOpenerButton.addActionListener(this);
		this.add(folderOpenerButton);

		textField = new JTextField();
		textField.setPreferredSize(new Dimension(300, 25));
		try {
			new File(last).createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String lastPath = readLastPath();
		textField.setText(lastPath == null ? ioProvider.defaultFolder : lastPath);
		this.add(textField);

		this.pack();
	}

	private String readLastPath() {
		String lastUsedPath = null;
		try (BufferedReader br = new BufferedReader(new FileReader(last))) {
			lastUsedPath = br.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return lastUsedPath;
	}

	private void writeLastPath(String lastPath) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(last))) {
			bw.write(lastPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == folderSelecterButton) {
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fileChooser.setCurrentDirectory(new File("."));
			int r = fileChooser.showDialog(this, "Select");
			if (r == JFileChooser.APPROVE_OPTION) {
				File file = new File(fileChooser.getSelectedFile().getAbsolutePath());
				textField.setText(file.toString());
			}
		} else if (e.getSource() == folderOpenerButton) {
			String path = textField.getText();
			synchronized (this.ioProvider) {
				this.ioProvider.folder = path;
				writeLastPath(path);
				this.ioProvider.notify();
			}
			this.setVisible(false);
		}
	}

	@Override
	public void run() {
		this.buildFrame();
		this.setVisible(true);
	}

}
