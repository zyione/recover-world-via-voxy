package recovery;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import com.google.gson.*;

/** Small first-stage desktop front end; all conversion logic stays in the tested core. */
public final class RecoveryApp {
    private final JFrame frame=new JFrame("Overworld Recovery · Minecraft 1.21.11");
    private final JTextArea log=new JTextArea(10,64);
    private final JTextField[] coords=new JTextField[6];
    private final JTextField output=new JTextField(42);
    private final JButton start=new JButton("Recover selected area"),sample=new JButton("Use small test area");
    private final JsonObject defaults;
    private final Path configFile;
    private Path lastOutput;
    private final JTextField sourceFolder=new JTextField(42);
    private final JButton scan=new JButton("Browse and scan Voxy folder...");
    private final JComboBox<SourceSnapshot.Candidate> databases=new JComboBox<>();
    private SourceSnapshot.Prepared prepared;
    private boolean busy;
    private RecoveryApp(Path file) throws Exception {
        configFile=file.toAbsolutePath().normalize(); defaults=JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){
            if(busy)JOptionPane.showMessageDialog(frame,"Please wait for this operation to finish. Interrupted recovery cannot resume yet.");
            else frame.dispose();
        }});
        JPanel content=new JPanel();content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));content.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        content.add(new JLabel("Recover cached Overworld blocks into a separate Minecraft 1.21.11 world."));
        content.add(new JLabel("Beta: saved blocks are checked; missing inventories, entities and moving-piston data cannot be restored."));
        content.add(Box.createVerticalStrut(14));
        content.add(new JLabel("Close Minecraft before selecting your Voxy cache. A verified working copy will be created."));
        sourceFolder.setEditable(false);content.add(sourceFolder);content.add(scan);
        content.add(new JLabel("Database to recover (dimension inferred from saved biomes)"));content.add(databases);
        scan.addActionListener(e->scanSource());
        start.setEnabled(false);
        JPanel grid=new JPanel(new GridLayout(4,3,8,8));grid.add(new JLabel("Inclusive bounds"));grid.add(new JLabel("Minimum"));grid.add(new JLabel("Maximum"));
        String[] names={"minX","maxX","minY","maxY","minZ","maxZ"};
        for(int i=0;i<3;i++) { grid.add(new JLabel(new String[]{"X","Y","Z"}[i]));for(int j=0;j<2;j++){int k=i*2+j;coords[k]=new JTextField(defaults.getAsJsonObject("bounds").get(names[k]).getAsString());grid.add(coords[k]);}}
        content.add(grid);content.add(Box.createVerticalStrut(12));
        content.add(new JLabel("New output folder (must not already exist)"));
        output.setText(Path.of(System.getProperty("user.home"),"VoxyRecovery","worlds","recovery-"+stamp()).toString());content.add(output);
        content.add(Box.createVerticalStrut(8));
        content.add(new JLabel("Missing data and everything outside the bounds become empty space. Original seed unknown."));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));buttons.add(sample);buttons.add(start);
        JButton open=new JButton("Open output folder");buttons.add(open);content.add(buttons);
        log.setEditable(false);log.setLineWrap(true);log.setWrapStyleWord(true);content.add(new JScrollPane(log));
        sample.addActionListener(e->{int[] values={-224,-193,64,95,-1152,-1121};for(int i=0;i<6;i++)coords[i].setText(""+values[i]);});
        open.addActionListener(e->{try{if(lastOutput!=null && Files.isDirectory(lastOutput))Desktop.getDesktop().open(lastOutput.toFile());}catch(Exception ex){log.append(ex.getMessage()+"\n");}});
        start.addActionListener(e->start());
        frame.setContentPane(content);frame.pack();frame.setLocationRelativeTo(null);
        String preview=System.getProperty("recovery.preview");
        if(preview==null)frame.setVisible(true);
        else {
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(content.getWidth(),content.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics=image.createGraphics();content.printAll(graphics);graphics.dispose();
            javax.imageio.ImageIO.write(image,"png",Path.of(preview).toFile());frame.dispose();
        }
    }
    private static String stamp(){return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));}
    private void start() {
        try {
            int[] v=new int[6];for(int i=0;i<6;i++)v[i]=Integer.parseInt(coords[i].getText().trim());
            Bounds b=new Bounds(v[0],v[1],v[2],v[3],v[4],v[5]);
            if(b.chunkCount()>100_000)throw new IllegalArgumentException("Prototype limit: 100,000 chunk columns per run.");
            if(prepared==null)throw new IllegalArgumentException("Select and scan a Voxy folder first.");
            var candidate=(SourceSnapshot.Candidate)databases.getSelectedItem();
            if(candidate==null || !candidate.dimension().equals("Overworld candidate"))throw new IllegalArgumentException("This release only supports Overworld candidates.");
            JsonObject job=new JsonObject();job.addProperty("minecraftVersion","1.21.11");job.add("bounds",Config.JSON.toJsonTree(b));job.addProperty("maxChunks",b.chunkCount());
            job.addProperty("originalEvidence",prepared.original().toString());job.addProperty("snapshot",prepared.snapshot().toString());job.addProperty("manifest",prepared.manifest().toString());job.addProperty("database",candidate.relativePath());
            job.addProperty("output",Path.of(output.getText().trim()).toAbsolutePath().normalize().toString());
            Path jobs=prepared.manifest().getParent().resolve("jobs");Files.createDirectories(jobs);Path saved=jobs.resolve(stamp()+".json");Files.writeString(saved,Config.JSON.toJson(job));
            Config checked=Config.read(saved);if(Files.exists(checked.output()))throw new IllegalArgumentException("Choose an output folder that does not already exist.");
            lastOutput=checked.output();setBusy(true);log.append("Starting "+b.chunkCount()+" chunk columns. Your source files stay read-only.\n");
            new SwingWorker<Void,String>() {
                protected Void doInBackground()throws Exception {Main.run(saved,this::publish);return null;}
                protected void process(java.util.List<String> lines){for(String line:lines)log.append(line+"\n");log.setCaretPosition(log.getDocument().getLength());}
                protected void done(){setBusy(false);try{get();log.append("Done. Read recovery-report.json before testing a copy in Minecraft.\n");}catch(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();log.append("Stopped: "+cause.getMessage()+"\n");}}
            }.execute();
        } catch(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage(),"Check settings",JOptionPane.ERROR_MESSAGE);}
    }
    private void setBusy(boolean value){busy=value;start.setEnabled(!value && prepared!=null);scan.setEnabled(!value);sample.setEnabled(!value);databases.setEnabled(!value);output.setEnabled(!value);for(var field:coords)field.setEnabled(!value);}
    private void scanSource() {
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Select .voxy folder");chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setFileHidingEnabled(false);
        if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        Path input=chooser.getSelectedFile().toPath();sourceFolder.setText(input.toString());prepared=null;databases.removeAllItems();setBusy(true);
        Path work=Path.of(System.getProperty("user.home"),"VoxyRecovery","jobs",stamp());
        log.append("Preparing verified copy in "+work+". Allow disk space for a full copy of the cache.\n");
        new SwingWorker<SourceSnapshot.Prepared,String>() {
            protected SourceSnapshot.Prepared doInBackground()throws Exception{return SourceSnapshot.prepare(input,work,this::publish);}
            protected void process(java.util.List<String> lines){for(String line:lines)log.append(line+"\n");log.setCaretPosition(log.getDocument().getLength());}
            protected void done(){try{prepared=get();for(var candidate:prepared.candidates())databases.addItem(candidate);for(var candidate:prepared.candidates())if(candidate.dimension().equals("Overworld candidate")){databases.setSelectedItem(candidate);break;}log.append("Copy ready. Check the selected database and coordinates, then recover.\n");}catch(Exception ex){log.append("Scan failed: "+(ex.getCause()==null?ex:ex.getCause()).getMessage()+"\n");}finally{setBusy(false);}}
        }.execute();
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->{try{new RecoveryApp(Path.of(args.length==0?"config/defaults.json":args[0]));}catch(Exception e){JOptionPane.showMessageDialog(null,e.toString(),"Cannot start converter",JOptionPane.ERROR_MESSAGE);}});}
}
