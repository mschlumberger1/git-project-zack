import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class GitInitialize {

    private File git;
    private File objects;
    private File index;
    private File head;

    public GitInitialize() {
        try {
            int count = 0;
            git = new File("git/");
            if (!git.mkdir()) {
                count++;
            }

            objects = new File(git, "objects/");
            if (!objects.mkdir()) {
                count++;
            }

            index = new File(git, "index");
            if (!index.createNewFile()) {
                count++;
            }

            head = new File(git, "HEAD");
            if (!head.createNewFile()) {
                count++;
            }

            if (count == 4) {
                System.out.println("Git Repository Already Exists");
            } else {
                System.out.println("Git Repository Created");
            }
        } catch (IOException e) {
            System.out.println("File Error: " + e);
        }
    }

    public static void main(String[] args) {
        GitInitialize git = new GitInitialize();
        try {
            System.out.println(hashFile("Hello.txt"));
            git.createBlob("Hello.txt");
            git.createBlob("testFolder/file1.txt");
            git.createBlob("testFolder/file2.txt");
            git.addEntry("Hello.txt");
            git.addEntry("testFolder/file1.txt");
            git.addEntry("testFolder/file2.txt");
            ArrayList<String> workingList = new ArrayList<>();
            workingList.add("blob 0a4d55a8d778e5022fab701977c5d840bbc486d0 Hello.txt");
            workingList.add("blob 31a160541e2078032acad6161a920951e50b5747 testFolder/file1.txt");
            workingList.add("blob ac182d3f19d9029668a47967a42029fb874c861f testFolder/file2.txt");
        } catch (IOException e) {
            System.out.println("Hashing Error: " + e);
        }
    }

    public static String hashFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("no such file: " + filePath);
        }
        byte[] fileBytes = Files.readAllBytes(path);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }

        byte[] hash = digest.digest(fileBytes);
        return HexFormat.of().formatHex(hash);
    }

    public void createBlob(String filePath) throws IOException {
        String hash = hashFile(filePath);
        File hashFile = new File(objects, hash);
        if (!hashFile.exists()) {
            hashFile.createNewFile();
        }
        FileWriter hashWriter = new FileWriter(hashFile.toPath().toString());
        BufferedReader hashReader = new BufferedReader(new FileReader(filePath));
        String contents = hashReader.readLine();
        if (contents != null) {
            hashWriter.write(contents);
            contents = hashReader.readLine();
        }
        while (contents != null) {
            hashWriter.write("\n" + contents);
            contents = hashReader.readLine();
        }
        hashReader.close();
        hashWriter.close();

    }

    public void addEntry(String filePath) throws IOException {
        String hash = hashFile(filePath);
        List<String> lines = new ArrayList<>();
        String line = "";
        BufferedReader indexReader = new BufferedReader(new FileReader(index.toPath().toString()));
        while ((line = indexReader.readLine()) != null) {
            lines.add(line);
        }
        List<String> indexEntries = new ArrayList<>();
        FileWriter hashWriter = new FileWriter(index.toPath().toString());
        // case 1: when nothing is in index
        if (lines == null || lines.isEmpty()) {
            indexEntries.add(hash + " " + filePath);
        } else {
            boolean existsPath = false;
            for (int i = 0; i < lines.size(); i++) {
                int indexHash = lines.get(i).indexOf(" ");
                if (lines.get(i).substring(indexHash + 1).equals(filePath)) {
                    existsPath = true;
                    // case 2: same path, different hash
                    indexEntries.add(hash + " " + filePath);
                } else {
                    // case 3: same path, same hash
                    indexEntries.add(lines.get(i));
                }
            }
            if (!existsPath) {
                // case 4: new path + hash
                indexEntries.add(hash + " " + filePath);
            }

        }
        String total = "";
        for (int j = 0; j < indexEntries.size(); j++) {
            if (j == 0) {
                total += indexEntries.get(j);
            } else {
                total += "\n" + indexEntries.get(j);
            }
        }
        hashWriter.write(total);
        hashWriter.close();
        indexReader.close();
    }

    // 3.2 - creates initial working list w/blobs
    public static ArrayList<String> createWorkingList(File index) throws IOException {
        ArrayList<String> workingList = new ArrayList<String>();
        BufferedReader indexReader = new BufferedReader(new FileReader(index.toPath().toString()));
        String line = "";
        while ((line = indexReader.readLine()) != null) {
            workingList.add("blob " + line);
        }
        indexReader.close();
        return workingList;
    }

    // 3.2 (but changed in 3.3) - takes a directory path and creates a tree of its direct
    // descendants
    // note that this was tested when i made it in 3.2 but didn't have a chance to test the new
    // version sorry
    public String createTree(ArrayList<String> workingList, String dirPath) throws IOException {
        ArrayList<String> trees = new ArrayList<>();
        for (String line : workingList) {
            String type = line.substring(0, line.indexOf(' '));
            String hash = line.substring(line.indexOf(' ') + 1, line.lastIndexOf(' '));
            String path = line.substring(line.lastIndexOf(' ') + 1);
            if (getParent(path).equals(dirPath)) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                trees.add(type + " " + hash + " " + name);
            }
        }
        String treeString = "";
        for (int i = 0; i < trees.size(); i++) {
            treeString += trees.get(i);
            if (i < trees.size() - 1) {
                treeString += "\n";
            }
        }
        String treeHash = hashContent(treeString);
        File treeFile = new File(objects, treeHash);
        if (!treeFile.exists()) {
            FileWriter treeWriter = new FileWriter(treeFile);
            treeWriter.write(treeString);
            treeWriter.close();
        }
        return treeHash;
    }

    // 3.3 - groups subdirectories
    public static ArrayList<String> sortWorkingList(ArrayList<String> workingList) {
        for (int i = 0; i < workingList.size(); i++) {
            if (!workingList.get(i).substring(0, 5).equals("blob ")
                    && !workingList.get(i).substring(0, 5).equals("tree ")) {
                workingList.set(i, "blob " + workingList.get(i));
            }
        }
        for (int i = 0; i < workingList.size(); i++) {
            for (int j = i + 1; j < workingList.size(); j++) {
                String lineOne =
                        workingList.get(i).substring(workingList.get(i).lastIndexOf(' ') + 1);
                String lineTwo =
                        workingList.get(j).substring(workingList.get(j).lastIndexOf(' ') + 1);
                if (slashes(lineOne) < slashes(lineTwo)) {
                    String tempSave = workingList.get(i);
                    workingList.set(i, workingList.get(j));
                    workingList.set(j, tempSave);
                } else if (slashes(lineOne) == slashes(lineTwo)) {
                    if (lineOne.compareTo(lineTwo) > 0) {
                        String tempSave = workingList.get(i);
                        workingList.set(i, workingList.get(j));
                        workingList.set(j, tempSave);
                    }
                }
            }
        }
        return workingList;
    }

    // 3.3 - this is the recursive method that does everything: it takes the deepest directory and
    // builds up trees from there until there is only one root tree
    public String compressWorkingList(ArrayList<String> workingList) throws IOException {
        String dirPath = findLeafMostDirectory(workingList);
        // base case
        if (dirPath.equals("")) {
            return createTree(workingList, "");
        }
        // non base case
        String treeHash = createTree(workingList, dirPath);
        collapseWorkingList(workingList, dirPath, treeHash);
        return compressWorkingList(workingList);
    }

    // helper methods that are explicitly (or at least basically) mentioned in the instructions

    // 3.3 - finds the deepest directory
    public String findLeafMostDirectory(ArrayList<String> workingList) {
        String deepestDir = "";
        int deepest = -1;
        for (String line : workingList) {
            String path = line.substring(line.lastIndexOf(" ") + 1);
            if (getParent(path) != "") {
                if (slashes(path) > deepest) {
                    deepest = slashes(path);
                    deepestDir = getParent(path);
                }
            }
        }
        return deepestDir;
    }

    // 3.3 - condenses working list to just have the tree already made vs multiple blobs
    public void collapseWorkingList(ArrayList<String> workingList, String dirPath,
            String treeHash) {
        for (int i = workingList.size() - 1; i >= 0; i--) {
            String line = workingList.get(i);
            String path = line.substring(line.lastIndexOf(' ') + 1);
            if (getParent(path).equals(dirPath)) {
                workingList.remove(i);
            }
        }
        workingList.add("tree " + treeHash + " " + dirPath);
    }


    // extra helper methods
    // variation of hashFile that just uses content
    public static String hashContent(String content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
        byte[] byteArray = content.getBytes(StandardCharsets.UTF_8);
        byte[] hash = digest.digest(byteArray);
        return HexFormat.of().formatHex(hash);
    }

    // finds the number of slashes (how deep) a directory is
    public static int slashes(String path) {
        int count = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                count++;
            }
        }
        return count;
    }

    // finds the direct parent of a file
    public String getParent(String path) {
        int slash = path.lastIndexOf("/");
        if (slash == -1) {
            return "";
        }
        return path.substring(0, slash);
    }



    // getters and setters
    /**
     * @return the git
     */
    public File getGit() {
        return git;
    }

    /**
     * @param git the git to set
     */
    public void setGit(File git) {
        this.git = git;
    }

    /**
     * @return the objects
     */
    public File getObjects() {
        return objects;
    }

    /**
     * @param objects the objects to set
     */
    public void setObjects(File objects) {
        this.objects = objects;
    }

    /**
     * @return the index
     */
    public File getIndex() {
        return index;
    }

    /**
     * @param index the index to set
     */
    public void setIndex(File index) {
        this.index = index;
    }

    /**
     * @return the head
     */
    public File getHead() {
        return head;
    }

    /**
     * @param head the head to set
     */
    public void setHead(File head) {
        this.head = head;
    }
}
