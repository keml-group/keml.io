package keml.io;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

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

	FileBrowseDialog(IOProvider ioProvider) {
		this.ioProvider = ioProvider;
	}

	private void buildFrame() {
		this.setTitle("Choose conversation files");
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLayout(new FlowLayout());
		ImageIcon logo = new ImageIcon("logos/camel.png");
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
		textField.setText(new File(ioProvider.defaultFolder).getAbsolutePath());
		this.add(textField);

		this.pack();
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
				this.ioProvider.notify();
			}
		}
	}

	@Override
	public void run() {
		this.buildFrame();
		this.setVisible(true);
	}

}
