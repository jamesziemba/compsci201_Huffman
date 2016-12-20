import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

public class TreeHuffProcessor implements IHuffProcessor {

	public HuffViewer myViewer;
	public static int inb;
	public int[] myCounts;
	public HashMap<Integer, Integer> counter;
	static HashMap<Integer,String> traversed;
	public static PriorityQueue<TreeNode> q;
	public static int[] counter1;
	public static BitInputStream bis;
	public static int inbits;
	public static int outbits;
	public static int bitsSaved;
	public static TreeNode input;
	int size;
	StringBuilder myHeader = new StringBuilder("");


	public int compress(InputStream in, OutputStream out, boolean force) throws IOException {
		BitInputStream bis = new BitInputStream(in);
		BitOutputStream bos = new BitOutputStream(out);
		size = 0;
		//calculate header and magic number size,
		
		size+= BITS_PER_INT; //add size of magic number
		size+= BITS_PER_INT; //size of STORE TREE
		if(size>bitsSaved && !force){
			throw new IOException("Compression does not save any space it uses this many extra bits: "+ (bitsSaved-size));
		}
		//System.out.println("Magic number and header sizes have been calculated.");
		//write out the magic number
		bos.writeBits(BITS_PER_INT, MAGIC_NUMBER);
		bos.writeBits(BITS_PER_INT, STORE_TREE);

		makeHeader(input,bos);
		//System.out.println("Magic number and header have been written.");
		inb = bis.readBits(BITS_PER_WORD);
		while(inb!=-1){
			String word = traversed.get(inb);
			for(int i = 0; i<word.length(); i++){
				bos.writeBits(1, Integer.parseInt(word.substring(i, i+1)));
				size++;
			}
			inb = bis.readBits(BITS_PER_WORD);
		}
		//System.out.println("Compressed file has been written.");
		String word = traversed.get(PSEUDO_EOF);
		for(int i = 0; i<word.length();i++){
			bos.writeBits(1, Integer.parseInt(word.substring(i, i+1)));
			size++;
		}
		//System.out.println("PSEUDO_EOF has been written.");
		bos.flush();
		bos.close();


		return bitsSaved-size;
	}

	

	public  void makeHeader(TreeNode root, BitOutputStream bos){
		if(root.myLeft!=null&&root.myRight!=null){
			bos.writeBits(1, 0);
			size++;
			makeHeader(root.myLeft,bos);
			makeHeader(root.myRight,bos);
		}
		if(root.myLeft==null&&root.myRight==null){
			bos.writeBits(1, 1);
			size++;
			bos.writeBits(BITS_PER_WORD+1, root.myValue);
			size+=BITS_PER_WORD+1;
			return;
		}
		
		return;
	}
	public int preprocessCompress(InputStream in) throws IOException {
		bis = new BitInputStream(in);
		count();
		q = new PriorityQueue<TreeNode>();
		makeQueue(counter1);
		input = makeTree();
		traversed = new HashMap<Integer,String>();
		traverse(input,"");
		bitsSaved = helpCompare();
		return bitsSaved;


	}
	public static void count() throws IOException{
		inb = bis.readBits(BITS_PER_WORD);
		inbits = 0;
		counter1= new int[ALPH_SIZE];
		//count frequency
		while(inb!=-1){
			inbits+=8;
			/*if(counter.containsKey(inb)){
				counter.put(inb,0);
			}
			counter.put(inb, counter.get(inb)+1); */
			counter1[inb]++;
			inb = bis.readBits(BITS_PER_WORD);
		}
	}
	public static void makeQueue(int [] array){
		//add all frequencies into a queue
		q.clear();
		for(int i = 0; i<array.length; i++ ){			
			TreeNode temp = new TreeNode(i, array[i]);
			q.add(temp);			
		}
		//add pseudo node
		TreeNode pseudo = new TreeNode(PSEUDO_EOF,1);
		q.add(pseudo);
	}
	public static TreeNode makeTree(){
		TreeNode smallest;
		TreeNode smaller;
		//combine nodes into one tree
		while(q.size()>1){
			smallest = q.poll();
			smaller = q.poll();
			TreeNode temp = new TreeNode(smaller.myWeight+smallest.myWeight*-10,smaller.myWeight+smallest.myWeight);
			temp.myLeft = smallest;
			temp.myRight = smaller;
			q.add(temp);
		}
		TreeNode root = q.poll();
		return root;
	}
	public static int helpCompare(){
		outbits = 0;
		for(int i : traversed.keySet()){
			if(i!=256){
				int j = counter1[i]*traversed.get(i).length();
				outbits+=j;
			}

		}
		return inbits-outbits;
	}
	//helper method to traverse tree and create map of encodings
	public static void traverse(TreeNode root,String s){
		if(root==null){
			return;
		}
		if(root.myLeft==null && root.myRight==null){
			traversed.put(root.myValue,s);
			return;
		}
		String myLeftVal = "0";
		String myRightVal = "1";
		traverse(root.myLeft,s+myLeftVal);
		traverse(root.myRight,s+myRightVal);
		return;
	}



	public void setViewer(HuffViewer viewer) {
		myViewer = viewer;
	}

	public TreeNode redoTree(BitInputStream bis) throws IOException{
		int intBit = bis.readBits(1);
		if(intBit == -1){	System.err.println("Should not happen! REDOTREE reading bits");}
		if( intBit== 0){
			TreeNode root = new TreeNode(0, 0, null, null);
			root.myLeft = redoTree(bis);
			root.myRight = redoTree(bis);
			return root;			
		}
		else{
			intBit = bis.readBits(9);
			TreeNode root = new TreeNode(intBit, 0, null, null);
			return root;
		}

	}

	public int uncompress(InputStream in, OutputStream out) throws IOException {
		BitInputStream bis = new BitInputStream(in);
		BitOutputStream bos = new BitOutputStream(out);
		myCounts = new int[ALPH_SIZE];
		int size = 0;
		int magic = bis.readBits(BITS_PER_INT);
		if (magic != MAGIC_NUMBER){
			throw new IOException("magic number not right");
		}
		//System.out.println("Magic number has been found.");
		int store = bis.readBits(BITS_PER_INT);
		if (store != STORE_TREE){
			throw new IOException("STORE number not right");
		}
		//System.out.println("The header is correct.");
		TreeNode root = redoTree(bis);
		//System.out.println("The tree has been remade.");
		TreeNode current = root;
		int intBit;
		while(true){
			intBit = bis.readBits(1);

			if(intBit == -1){
				System.err.println("Should not happen! trouble reading bits");
			}
			else{
				if((intBit & 1) == 0){
					current = current.myLeft;

				}
				else{
					current = current.myRight;
				}

				//if a leaf, write in bits and increment size
				if(current.myLeft == null && current.myRight == null){
					if(current.myValue == PSEUDO_EOF){
						break;
					}
					else{
						bos.writeBits(BITS_PER_WORD, current.myValue);
						size+= BITS_PER_WORD;
						current = root;
					}
				}
			}
		}
		//System.out.println("The file has been rewritten successfully.");
		bos.close();


		//System.out.println(size);
		return size;
	}

	private void showString(String s){
		myViewer.update(s);
	}
	public static void main(String[] args) throws IOException{
		SimpleHuffProcessor test = new SimpleHuffProcessor();
		String string = "aaaaaaaaaassssssspprrrrrrtttooooooooo";



		//use ByteArrayInputStream to get the bytes of the String and convert them to InputStream.

		InputStream inputStream = new ByteArrayInputStream(string.getBytes(Charset.forName("UTF-8")));
		BitInputStream in = new BitInputStream(inputStream);
		//System.out.println(test.preprocessCompress(in));
	}
}