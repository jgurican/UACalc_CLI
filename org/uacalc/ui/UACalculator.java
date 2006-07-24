
package org.uacalc.ui;

import javax.swing.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.prefs.*;


import org.uacalc.alg.*;
import org.uacalc.lat.*;
import org.uacalc.io.*;


public class UACalculator extends JFrame {

  private boolean dirty = false;

  private SmallAlgebra algebra;  // Small ??
  private File currentFile;
  private String currentFolder;
  private JPanel mainPanel;
  private JPanel bottomPanel;
  private LatDrawPanel latDrawPanel;

  public UACalculator() {
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    //closes from title bar and from menu
    addWindowListener(new WindowAdapter() {
        public void windowClosing (WindowEvent e) {
          if (isDirty()) {
            if (checkSave()) {
              System.exit(0);
            }
          }
          else {
            System.exit(0);
          }
        }
      });
    buildMenu();

    mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());
//    mainPanel.add(toolBar, BorderLayout.NORTH);
//    mainPanel.add(shaper, BorderLayout.CENTER);
//    dimensionsPanel = new DimensionsPanel(this);
//    pointPanel = new PointPanel(this);
    bottomPanel = new JPanel();
    bottomPanel.setBackground(Color.CYAN);
    bottomPanel.setLayout(new BorderLayout());
//    bottomPanel.add(pointPanel, BorderLayout.EAST);
//    bottomPanel.add(dimensionsPanel, BorderLayout.CENTER);
    mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    latDrawPanel = new LatDrawPanel();
    mainPanel.add(latDrawPanel, BorderLayout.CENTER);

    buildMenu();
    setContentPane(mainPanel);

  }

  private void buildMenu() {
    // Instantiates JMenuBar, JMenu and JMenuItem
    JMenuBar menuBar = new JMenuBar();

    // the file menu
    JMenu file = (JMenu) menuBar.add(new JMenu("File"));
    file.setMnemonic(KeyEvent.VK_F);

    ClassLoader cl = this.getClass().getClassLoader();


    ImageIcon icon = new ImageIcon(cl.getResource(
                             "org/uacalc/ui/images/New16.gif"));

    JMenuItem newMI = (JMenuItem)file.add(new JMenuItem("New", icon));
    newMI.setMnemonic(KeyEvent.VK_N);
    KeyStroke cntrlN = KeyStroke.getKeyStroke(KeyEvent.VK_N,Event.CTRL_MASK);
    newMI.setAccelerator(cntrlN);

    file.add(new JSeparator());

    ImageIcon openIcon = new ImageIcon(cl.getResource(
                             "org/uacalc/ui/images/Open16.gif"));
    JMenuItem openMI = (JMenuItem)file.add(new JMenuItem("Open", openIcon));
    openMI.setMnemonic(KeyEvent.VK_O);
    KeyStroke cntrlO = KeyStroke.getKeyStroke(KeyEvent.VK_O,Event.CTRL_MASK);
    openMI.setAccelerator(cntrlO);

    icon = new ImageIcon(cl.getResource("org/uacalc/ui/images/SaveAs16.gif"));

    JMenu saveAsMenu = (JMenu)file.add(new JMenu("Save As"));
    JMenuItem saveAsXMLMI
      = (JMenuItem)saveAsMenu.add(new JMenuItem("XML file", icon));
    JMenuItem saveAsAlgMI
      = (JMenuItem)saveAsMenu.add(new JMenuItem("alg file (old format)", icon));

    saveAsXMLMI.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            saveAs(ExtFileFilter.XML_EXT);
          }
          catch (IOException ex) {
            System.err.println("IO error in saving: " + ex.getMessage());
          }
        }
      });

    saveAsAlgMI.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            saveAs(ExtFileFilter.ALG_EXT);
          }
          catch (IOException ex) {
            System.err.println("IO error in saving: " + ex.getMessage());
          }
        }
      });


    JMenuItem exitMI = (JMenuItem)file.add(new JMenuItem("Exit"));
    exitMI.setMnemonic(KeyEvent.VK_X);
    KeyStroke cntrlQ = KeyStroke.getKeyStroke(KeyEvent.VK_Q,Event.CTRL_MASK);
    exitMI.setAccelerator(cntrlQ);

    JMenu draw = (JMenu) menuBar.add(new JMenu("Draw"));

    JMenuItem drawBelindaMI = (JMenuItem)draw.add(new JMenuItem("Draw Belinda"));
    drawBelindaMI.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          drawBelinda(getAlgebra());
        }
      });

    setJMenuBar(menuBar);

    openMI.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          //should this if statement go inside the try?
             try {
               open();
             }
             catch (IOException err) {
               err.printStackTrace();
             }
          }
      });

    exitMI.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          System.exit(0);
        }
      });

  }

  public LatDrawPanel getLatDrawPanel() { return latDrawPanel; }

  public void drawBelinda(SmallAlgebra alg) {
    Operation op = null;
    for (Iterator it = alg.operations().iterator(); it.hasNext(); ) {
      op = (Operation)it.next();
      if (Operations.isCommutative(op) && Operations.isIdempotent(op)
                                      && Operations.isAssociative(op)) break;
    }
    if (op == null) {
      System.out.println("Could not find a semilattice operations.");
      return;
    }
    java.util.List univ = new ArrayList(alg.universe());
    BasicLattice lat = Lattices.latticeFromMeet("", univ, op);
    //LatDrawer.drawLattice(lat);
    try {
      getLatDrawPanel().setDiagram(lat.getDiagram());
    }
    catch (org.latdraw.orderedset.NonOrderedSetException e) {
      e.printStackTrace();
    }
    repaint();
  }

  public boolean saveAs(String ext) throws IOException {
    if (getAlgebra() == null) return true;
    boolean newFormat = true;
    if (ext.equals(ExtFileFilter.ALG_EXT)) newFormat = false;
    String pwd = getPrefs().get("algebraDir", null);
    if (pwd == null) pwd = System.getProperty("user.dir");
    JFileChooser fileChooser;
    if (pwd != null)
      fileChooser = new JFileChooser(pwd);
    else
      fileChooser = new JFileChooser();

    fileChooser.addChoosableFileFilter(
         newFormat ? 
         new ExtFileFilter("Alg Files New Format (*.xml)", 
                            ExtFileFilter.XML_EXT) :
         new ExtFileFilter("Alg Files Old Format (*.alg)", 
                            ExtFileFilter.ALG_EXT));
    int option = fileChooser.showSaveDialog(this);
    if (option==JFileChooser.APPROVE_OPTION) {
      // save original user selection
      File selectedFile = fileChooser.getSelectedFile();
      File f = selectedFile;
      // if it doesn't end in .brd, add ".brd" even if there already is a "."
      if (f.exists()) {
        Object[] options = {"Yes", "No"};
        int n = JOptionPane.showOptionDialog(this,
                                "The file already exists. Overwrite?",
                                "Board Exists",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null,
                                options,
                                options[0]);
        if (n == JOptionPane.NO_OPTION) {
          saveAs(ext);
        }
      }
      String extension = ExtFileFilter.getExtension(f);
      if (extension == null || !extension.equals(ext)) {
        f = new File(f.getCanonicalPath() + "." + ext);
      }
      AlgebraIO.writeAlgebraFile(getAlgebra(), f, !newFormat);
      // setModified(false);
      setTitle();
      return true;
    }
    return false;
  }






  public void open() throws IOException {
    String pwd = getPrefs().get("algebraDir", null);
    if (pwd == null) pwd = System.getProperty("user.dir");
    SmallAlgebra a = null;
    File theFile = null;
    //pwd = currentFolder;
    JFileChooser fileChooser;
    if (pwd != null) fileChooser = new JFileChooser(pwd);
    else fileChooser = new JFileChooser();
    //fileChooser.addChoosableFileFilter(
    //     new ExtFileFilter("Shape3D Files (*.s3d)", ExtFileFilter.S3D_EXT));
    //fileChooser.addChoosableFileFilter(
    //     new ExtFileFilter("Board Files (*.brd)", ExtFileFilter.BOARD_EXT));
    //fileChooser.setAccessory(new CurvePreviewer(this, fileChooser));
    int option = fileChooser.showOpenDialog(this);

    if (option==JFileChooser.APPROVE_OPTION) {
      theFile = fileChooser.getSelectedFile();
      currentFolder = theFile.getParent();
      getPrefs().put("algebraDir", theFile.getParent());
      open(theFile);
    }
  }

  public void open(File file) {
    getPrefs().put("algebraDir", file.getParent());
    SmallAlgebra a = null;
    try {
      a = AlgebraIO.readAlgebraFile(file);
    }
    catch (BadAlgebraFileException e) {
      System.err.println("Bad algebra file " + file);
      e.printStackTrace();
      beep();
    }
    catch (IOException e) {
      System.err.println("IO error on file " + file);
      e.printStackTrace();
      beep();
      //setUserMessage("Can't find the file: " + file);
      //getDimensionsPanel().setInfoDialogTextColor(Color.RED);
    }
    catch (NullPointerException e) {
      //setUserMessage("Open Failed. Choose a .brd file or type correctly.");
      //getDimensionsPanel().setInfoDialogTextColor(Color.RED);
      System.err.println("open failed");
      beep();
    }
    if (a != null) {
      //this is to get rid of the left over error messages below
      //setUserMessage("");
      setCurrentFile(file);
      setTitle();
      //setModified(false);
      setAlgebra(a);
    }
System.out.println("con of loaded alg size : " + getAlgebra().con().cardinality());
  }

  public File getCurrentFile() { return currentFile; }
  public void setCurrentFile(File f) { currentFile = f; }
  


  public SmallAlgebra getAlgebra() { return algebra; }
  public void setAlgebra(SmallAlgebra alg) { algebra = alg; }

  public boolean isDirty() { return dirty; }

  public void beep() {
    Toolkit.getDefaultToolkit().beep();
  }

  public void setTitle() { setTitle(currentFile.getName()); }


  public boolean checkSave() { return true; }

  // prefs stuff

  public Preferences getPrefs() {
    return Preferences.userNodeForPackage(this.getClass());
  }


  public static void main(String[] args) {
    UACalculator frame = new UACalculator();
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    int width = (screenSize.width * 9) / 10;
    int height = (screenSize.height * 9) / 10;
    frame.setLocation((screenSize.width - width) / 2,
                      (screenSize.height - height) / 2);
    frame.setSize(width, height);
    frame.isDefaultLookAndFeelDecorated();

    Runnable  runner = new FrameShower(frame);
    EventQueue.invokeLater(runner);
  }



  private static class FrameShower implements Runnable {
    final JFrame frame;

    public FrameShower(JFrame frame) {
      this.frame = frame;
    }

    public void run() {
      frame.setVisible(true);
      //JOptionPane.showMessageDialog(frame,
      //    "This version of the program is out of date."
      //    + "\nGet the new version at www.aps3000.com"
      //    + "\nClick on Software");
    }
  }



}
