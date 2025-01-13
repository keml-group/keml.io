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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class FileBrowseDialog extends JFrame implements ActionListener, Runnable {

	JPanel p1;
	JPanel p2;
	JPanel p3;
	JButton folderSelecterButton;
	JButton folderOpenerButton;
	JTextField textField;
	JLabel message;

	IOProvider ioProvider;

	String lastPathSave = "utils/paths/last_used_paths.txt";
	String lastPath;

	FileBrowseDialog(IOProvider ioProvider) {
		this.ioProvider = ioProvider;
	}

	private void buildFrame() {
		this.setTitle("Choose conversation files");
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setSize(400, 200);
		this.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
		ImageIcon logo = new ImageIcon("utils/logos/camel.png");
		this.setIconImage(logo.getImage());
		this.setLocationRelativeTo(null);
		this.setAlwaysOnTop(true);

		p1 = new JPanel();
		p1.setPreferredSize(new Dimension(390, 35));
		this.add(p1);

		textField = new JTextField();
		textField.setPreferredSize(new Dimension(300, 25));
		try {
			new File(lastPathSave).createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String lp;
		lastPath = (lp = readLastPath()) == null ? ioProvider.defaultFolder : lp;
		textField.setText(lastPath);
		p1.add(textField);

		p2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 0));
		p2.setPreferredSize(new Dimension(390, 30));
		this.add(p2);

		folderSelecterButton = new JButton("Select folder");
		folderSelecterButton.addActionListener(this);
		p2.add(folderSelecterButton);

		folderOpenerButton = new JButton("Open folder >>");
		folderOpenerButton.addActionListener(this);
		p2.add(folderOpenerButton);

		p3 = new JPanel(new FlowLayout());
		message = new JLabel();
		p3.add(message);

		this.add(p3);
	}

	private String readLastPath() {
		String lastUsedPath = null;
		try (BufferedReader br = new BufferedReader(new FileReader(lastPathSave))) {
			lastUsedPath = br.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return lastUsedPath;
	}

	private void writeLastPath(String lastPath) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(lastPathSave))) {
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
			fileChooser.setCurrentDirectory(new File(textField.getText()));
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