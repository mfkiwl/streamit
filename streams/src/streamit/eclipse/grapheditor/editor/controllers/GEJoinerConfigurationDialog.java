/*
 * Created on Jan 28, 2004
 */
package streamit.eclipse.grapheditor.editor.controllers;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import streamit.eclipse.grapheditor.editor.pad.GPDocument;
import streamit.eclipse.grapheditor.editor.pad.resources.Translator;

/**
 * Configuration dialog for a GEJoiner. 
 * @author jcarlos
 */
public class GEJoinerConfigurationDialog  extends GEStreamNodeConfigurationDialog{


	private String dialogType = "Joiner Configuration";

	protected JLabel joinerWeightsLabel = new JLabel();
	protected JTextField joinerWeightsTextField;
 
	
	/**
	 * Creates new form GEJoinerConfigurationDialog
	 */
	public GEJoinerConfigurationDialog(Frame parent, GPDocument document) 
	{    
		super(parent, document);
		initComponents();
		setTitle(dialogType);
		setName(dialogType);		
		setPosition();
	}

	/** 
	 * Called by pressing the ok button.
	 */
	protected void action_ok() {
		
		/*
		try {
			Integer.parseInt(nameTextField.getText());
			Integer.parseInt(parentTextField.getText());
			Integer.parseInt(inputTapeTextField.getText());
			Integer.parseInt(outputTapeTextField.getText());
			Integer.parseInt(argumentsTextField.getText());
		} catch (Exception e) {
			String message = Translator.getString("Error.SpacingMustBeNumbers");
			JOptionPane.showMessageDialog(this, message, Translator.getString("Error"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}*/
		
		setVisible(false);
		dispose();
		canceled = false;
	}
    
	/**
	 * Set the value of the "Push Rate" text field.
	 * @param push Text value for "Push Rate"
	 */
	public void setJoinerWeights(String weights)
	{
		joinerWeightsTextField.setText(weights);	
	}
	
	/**
	 * Set the value of the "Pop Rate" text field.
	 * @param push Text value for "Pop Rate"
	 */
	public String getJoinerWeights()
	{
		return joinerWeightsTextField.getText().trim();
	}

	/** 
	 * Initialize the Swing Components
	 */   
	protected void initComponents() 
	{
		jPanel1 = new JPanel(new GridLayout(7,7));
		toolBar = new JPanel(new FlowLayout(FlowLayout.RIGHT ));
		cancelButton = new JButton();
		finishedButton = new JButton();

		nameLabel = new JLabel();
		parentLabel = new JLabel();
		inputTapeLabel = new JLabel();
		outputTapeLabel = new JLabel();
		joinerWeightsLabel = new JLabel();

	
		nameTextField = new JTextField();
		inputTapeTextField = new JTextField();
		outputTapeTextField = new JTextField();
		joinerWeightsTextField = new JTextField();
		
		parentsJComboBox = new JComboBox(this.document.getGraphStructure().containerNodes.getAllContainerNames());

		addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(java.awt.event.WindowEvent evt) {
				closeDialog(evt);
			}
		});

		finishedButton.setText(Translator.getString("OK"));
		finishedButton.setName(Translator.getString("OK"));
		
		finishedButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				finishedButtonActionPerformed(evt);
			}
		});

		toolBar.add(finishedButton);
		getRootPane().setDefaultButton(finishedButton);

		cancelButton.setText(Translator.getString("Cancel"));
		cancelButton.setName(Translator.getString("Cancel"));
		cancelButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				cancelButtonActionPerformed(evt);
			}
		});
		toolBar.add(cancelButton);

		getContentPane().add(toolBar, BorderLayout.SOUTH );        

		nameLabel.setText("Name");
		nameLabel.setName("Name");
		jPanel1.add(nameLabel);
		jPanel1.add(nameTextField);

		parentLabel.setText("Parent");
		parentLabel.setName("Parent");
		jPanel1.add(parentLabel);
		jPanel1.add(parentsJComboBox);
		
		inputTapeLabel.setText("Input Tape");
		inputTapeLabel.setName("Input Tape");
		jPanel1.add(inputTapeLabel);
		jPanel1.add(inputTapeTextField);
		
		outputTapeLabel.setText("Output Tape");
		outputTapeLabel.setName("Output Tape");
		jPanel1.add(outputTapeLabel);
		jPanel1.add(outputTapeTextField);

		joinerWeightsLabel.setText("Joiner Weights");
		joinerWeightsLabel.setName("JoinerWeights");
		jPanel1.add(joinerWeightsLabel);
		jPanel1.add(joinerWeightsTextField);
		
		JLabel weightParseReqLabel= new JLabel();
		weightParseReqLabel.setName("Weights Parse Req");
		weightParseReqLabel.setText("(separate weights with commas)");
		jPanel1.add(weightParseReqLabel);
		
		getContentPane().add(jPanel1, java.awt.BorderLayout.CENTER);

		pack();
	}


}