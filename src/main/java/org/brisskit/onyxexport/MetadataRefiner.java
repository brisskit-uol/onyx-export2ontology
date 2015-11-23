/**
 * 
 */
package org.brisskit.onyxexport;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import org.brisskit.export.metadata.config.beans.*;

import org.brisskit.onyxmetadata.stageone.beans.EntityType;
import org.brisskit.onyxmetadata.stageone.beans.QuestionType;
import org.brisskit.onyxmetadata.stageone.beans.RestrictionType;
import org.brisskit.onyxmetadata.stageone.beans.SectionType;
import org.brisskit.onyxmetadata.stageone.beans.SourceDocument;
import org.brisskit.onyxmetadata.stageone.beans.SourceType;
import org.brisskit.onyxmetadata.stageone.beans.StageType;
import org.brisskit.onyxmetadata.stageone.beans.VariableType;
import org.brisskit.onyxmetadata.stagetwo.beans.Container;
import org.brisskit.onyxmetadata.stagetwo.beans.ContainerDocument;
import org.brisskit.onyxmetadata.stagetwo.beans.Folder;
import org.brisskit.onyxmetadata.stagetwo.beans.Type;
import org.brisskit.onyxmetadata.stagetwo.beans.Variable;
import org.brisskit.onyxmetadata.stagetwo.enumeratedconcept.beans.EnumeratedVariableDocument;
import org.brisskit.onyxmetadata.stagetwo.enumeratedconcept.beans.RevEnumeratedVariable;
import org.brisskit.onyxmetadata.stagetwo.enumeratedconcept.beans.RevGroup;
import org.brisskit.onyxmetadata.stagetwo.enumeratedconcept.beans.RevType;
import org.brisskit.onyxmetadata.stagetwo.enumeratedconcept.beans.RevVariable;

/**
 * The <code>MetadataRefiner</code> class represents the process of converting Onyx metadata into a form suitable
 * for emitting SQL inserts into an i2b2 ontology table. That is an important point: the idea is to produce XML
 * suitable for producing an i2b2 ontology. The process <b>is</b> a process of refinement. <br/>
 * <br/>
 * The input is a directory of XML metadata files which have already been produced by processing an Onyx export zip file. 
 * The output consists of a collection of XML files: the main refined metadata file and a series of smaller files each
 * representing a single enumerated concept. There can be many hundreds of these.<br/>
 * <br/>
 * An enumerated concept is something like Age (in years) where every single instance of an age can be represented by
 * a separate concept. For example: <br/>
 * <p><blockquote><pre>
 *     Age 0 
 *     Age 1
 *     Age 2
 *     Age 3
 *     ...
 *     Age 100
 *     Age 101
 * </pre></blockquote><p>
 * For a person to have a specific age would then entail a pointer to one of these concepts from within the i2b2 
 * observation_fact table. Remember: every fact has a date, so using an enumerated Age concept would mean age at
 * a point in time. <br/>
 * <br/>
 * The enumerated concept files represent bottom leaves of an ontology tree held within the main refined metadata file.
 * <p/>
 * <b>NB: The codes produced by this programme are makeshift codes; ie: not SNOMED.</b>
 * <p/>
 * The process is currently a command line invocation. See section <a href="MetadataRefiner.html#usage">Usage</a>
 * 
 * 
 * @author  Jeff Lusted jl99@le.ac.uk
 *
 */
public class MetadataRefiner {
	
	private static Log log = LogFactory.getLog( MetadataRefiner.class ) ;
	
	/**
	 * The process is currently a command line invocation with the following usage:
	 * <p><blockquote><pre>
	 *     Usage: MetadataRefiner {Parameters}       
	 *       Parameters:
	 *        -input=path-to-onyx-metadata-directory
	 *        -config=path-to-xml-config-file
	 *        -refine=path-to-main-refine-directory
	 *        -enum=path-to-main-refine-enum-directory
	 *        -name=refined-metadata-file-name
	 *       Notes:
	 *        (1) Parameter triggers can be shortened to the first letter; ie: -i, -c, -r,-e,-n.
	 *        (2) All parameters are mandatory.
	 *        (3) The input path must exist.
	 *        (4) The config file must exist.
	 *        (5) The refine and enum paths must not exist.
     *        (6) Suggested refined metadata file name: Refined-Metadata.xml.
     * </pre></blockquote><p> 
	 */
	private static final String USAGE =
        "Usage: MetadataRefiner {Parameters}\n" +       
        "Parameters:\n" +
        " -input=path-to-onyx-metadata-directory\n" +
        " -config=path-to-xml-config-file\n" +
        " -refine=path-to-main-refine-directory\n" +
        " -enum=path-to-main-refine-enum-directory\n" +
        " -name=refined-metadata-file-name\n" +
        "Notes:\n" +
        " (1) Parameter triggers can be shortened to the first letter; ie: -i,-r,-e,-n.\n" +
        " (2) All parameters are mandatory.\n" +
        " (3) The input path must exist.\n" +
        " (4) The config file must exist.\n" +
        " (5) The refine and enum paths must not exist.\n" +
        " (6) Suggested refined metadata file name: Refined-Metadata.xml." ;
	
	public static final String[] ONYX_CONTINUOUS_TYPES = 
	{ "DATETIME", "DECIMAL", "INTEGER", "TEXT" } ;
	
	/**
	 * Two dimensional array giving a code/id and a description for vital_status.
	 * These values are derived from the i2b2 documentation, but do meld up completely
	 * with the i2b2 demo systems. The demo system(s) support another enumeration: Deferred.
	 * Please see the i2b2 Design Document for the Data Repository or CRC Cell.
	 */
	public static final String[][] VITAL_STATUS =
	{
		{ "N", "Living" } ,
		{ "Y", "Deceased" } ,
		{ "@", "Not recorded" } 
	} ;
	
    private String inDirectoryPath = null ;
    private String configFilePath = null ;
    private String outRefDirectoryPath = null ;
    private String outEnumDirectoryPath = null ;
    private String mainFileName = null ;
    
    private File inputDirectory ;
	String[] fileNames ;
	File outputDirectory ;
	
	protected OnyxExportConfigDocument configDoc ;
	protected OntologyPhaseType ontologyPhase ;
	protected SourceDocument currentSourceDoc ;
	protected ContainerDocument containerDoc ;
	
	private IExport2Ontology userDefinedProcess ;
	
	private LinkedHashMap<String,EnumType> enumerations ;
	private String[] standardBooleans ;
	private CodeType[] ethnicCodes ;
	private String ethnicityVariableName ;
	private HashMap<String, FilterType> questionnaireFilters ;
	
	/**
	 * @author jl99
	 *
	 */
	private class SiblingHolder {
		public boolean processed = false ;
		public VariableType parent ;
		public ArrayList<VariableType> siblingList = new ArrayList<VariableType>() ;
	}
	private SiblingHolder siblingHolder = new SiblingHolder() ;
	
	private HashSet<String> codeContainer = new HashSet<String>() ;
	
	private static StringBuffer logIndent = null ;

	
	/**
	 * Represents the main workflow control:
     * <p><blockquote><pre>
     *    1. Vets the input parameters.
     *    2. Vets the input directory for existence.
     *    3. Vets the two output directories for non-existence.
     *    4. Creates the two output directories.
     *    5. Processes each metadata file in the input directory.
     *    6. Saves the main refined metadata file.
     * </pre></blockquote><p>
     * NB: Individual enumerated concept files are produced throughout
     * the process. The main refined metadata file is only saved at
     * the end. It is the latter which really represents the ontology tree.
     * 
	 * @param args  See section <a href="MetadataRefiner.html#USAGE">Usage</a>
	 */
	public static void main( String[] args ) {
		
		MetadataRefiner mdr = new MetadataRefiner() ;
		
		//
		// Retrieve command line arguments...
		boolean good = mdr.retrieveArgs( args ) ;		
		if( !good ) {
			System.out.println( USAGE ) ;
			mdr.printArgs( args ) ;
			System.exit(1) ;
		}
		
		//
		// Vet args and make output directories if all is well...
		try {
			mdr.initialize() ;
		}
		catch( MetadataRefinerException mrx ) {
			System.out.println( mrx.getLocalizedMessage() ) ; 
			mrx.printStackTrace() ;
			System.exit(1) ;
		}

		//
		// Process the input directory...
		try {								
			mdr.processInputDirectory() ;
			System.out.println( "MetadataRefiner: Done!" ) ;	
			System.exit(0) ;
		}
		catch( Exception ex ) {
			ex.printStackTrace() ; 
			System.exit( 1 ) ;
		}

	}
	
    /**
     * Initializes a newly created {@code MetadataRefiner} object, establishing the main XML 
     * refined metadata file.
     */
	public MetadataRefiner() {
		this.containerDoc = ContainerDocument.Factory.newInstance() ;
	}
	
	public void processInputDirectory() throws Exception {
		
		for( int i=0; i<fileNames.length; i++ ) {
			File file = new File( inputDirectory, fileNames[i] ) ;
			if( file.isHidden() || file.isDirectory() ) {
				continue ;
			}
			log.info( "MetadataRefiner processing: " + file.getAbsolutePath() ) ;
			processFile( file ) ;
		}

		//
		// Save the main refined metadata file...
		save( outRefDirectoryPath + File.separator + mainFileName ) ;
		
	}
	
	/**
	 * Parses a single Onyx metadata file. The file represents either an Entity (eg: Participant) or
	 * a Stage (eg: MedicalHistoryQuestionnaire) and this method invokes the appropriate process routine.
	 * 
	 * @param file
	 * @throws org.apache.xmlbeans.XmlException
	 * @throws java.io.IOException
	 */
	public void processFile( File file )  throws org.apache.xmlbeans.XmlException, java.io.IOException {
		if( log.isTraceEnabled() ) enterTrace( "processFile" ) ;
		try {
			//
			// Parse the in-file into XmlBeans...
			currentSourceDoc = SourceDocument.Factory.parse( file ) ;
			SourceType source = currentSourceDoc.getSource() ;
			//
			// Get the entity or stage name as represented by the file name...
			String name = file.getName().split( "\\.")[0] ;
			//
			// These things are either entities or stages...
			if( source.isSetEntity() ) {
				processEntity( source.getEntity(), name ) ;
			}
			else if( source.isSetStage() ) {
				processStage( source.getStage(), name ) ;
			}
			else {
				throw new IOException( "Source file containes neither Entity nor Stage: " + file.getName() ) ;
			}	
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processFile" ) ;
		}
	}
	
	/**
	 * Processes the given entity. With Onyx this is currently only a Participant.
	 * An entity has little structure, basically a set of variables (eg: age, ethnicity, 
	 * and so on). Each variable is processed within its entity folder.
	 * 
	 * @param entity
	 * @throws IOException
	 */
	private void processEntity( EntityType entity, String name ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processEntity" ) ;
		try {
			//
			// Let any user defined function have first crack...
			if( isUserExcluded( entity ) ) {
				log.warn( "Entity excluded by user defined function: " + entity.getName() ) ;
				return ;
			}
			
			if( !isIncluded( entity ) )
				return ;
			
			Folder folder = containerDoc.getContainer().addNewFolder() ;
			folder.setName( name ) ;
			VariableType[] vta = entity.getVariableArray() ;
			for( int i=0; i<vta.length; i++ ) {
				processVariable( vta[i], folder, false ) ;
			}
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processEntity" ) ;
		}
	}
	
	/**
	 * Processes a stage in the Onyx questionnaire (eg: MedicalHistoryQuestionnaire).
	 * A stage is a major structure, having the following form:
     * <p><blockquote><pre>
     * stage-+
     *       |
     *       +--section-X-+
     *       |            |
     *       |            +--question-A-+
     *       |            |             |
     *       |            |             +--Variable-1
     *       |            |             |
     *       |            |             +--Variable-2
     *       |            |
     *       |            +--question-B-+
     *       |                          |
     *       +--section-Y-+             +--Variable-3
     *       |            |
     *       |            +--question-C-+
     *       |                          |
     *       |                          +--question-D-+
     *       +--Variable-n                            |
     *                                                +--Variable-n
     * </pre></blockquote><p>
	 * That is, a stage can have variables and sections as children. A section can itself consist
	 * of questions. Questions can contain variables or other questions. 
	 * Some questions can be information only, containing neither other questions nor variables.
	 * 
	 * @param stage
	 * @throws IOException
	 */
	private void processStage( StageType stage, String name ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processStage" ) ;
		try {
			//
			// Let any user defined function have first crack...
			if( isUserExcluded( stage ) ) {
				log.warn( "Stage excluded by user defined function: " + stage.getName() ) ;
				return ;
			}
			
			if( !isIncluded( stage ) )
				return ;
				
			Folder folder = containerDoc.getContainer().addNewFolder() ;
			folder.setName( name ) ;
			if( stage.sizeOfSectionArray() > 0 ) {
				SectionType[] sta = stage.getSectionArray() ;
				for( int i=0; i<sta.length; i++ ) {
					processSection( sta[i], folder ) ;
				}
			}
			if( stage.sizeOfVariableArray() > 0 ) {
				VariableType[] vta = stage.getVariableArray() ;
				for( int i=0; i<vta.length; i++ ) {
					processVariable( vta[i], folder, false ) ;
				}
			}
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processStage" ) ;
		}
	}
	
	/**
	 * Processes a section of a stage in an Onyx questionnaire. 
	 * As a section can only contain questions, in effect invokes
	 * the processing for each question. 
	 * 
	 * @param section
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processSection( SectionType section, Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processSection" ) ;
		try {
			//
			// Let any user defined function have first crack...
			if( isUserExcluded( section ) ) {
				log.warn( "Section excluded by user defined function: " + section.getName() ) ;
				return ;
			}
			
			if( !isIncluded( section ) ) 
				return ;
			
			Folder folder = parentFolder.addNewFolder() ;
			folder.setName( section.getName() ) ;
			QuestionType[] qta = section.getQuestionArray() ;
			for( int i=0; i<qta.length; i++ ) {
				processQuestion( qta[i], folder ) ;
			}
			
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processSection" ) ;
		}
	}
	
	private boolean isIncluded( XmlObject target ) {
		//
		// We include by default...
		boolean retCode = true ;
		//
		// First build a collection of path parts for this object...	
		Deque<String> pathParts = new ArrayDeque<String>() ;
	
		XmlCursor cursor = target.newCursor() ;	
		try {
			pathParts.push( getName(target) ) ;
			while( cursor.toParent() ) {
				XmlObject xo = cursor.getObject() ;
				if( xo instanceof SourceDocument == false ) {
					pathParts.push( getName( xo ) ) ;
				}			
			} ;
			//
			// We don't need the root of the tree...
			pathParts.pop();
		}
		finally {
			//
			// Make sure we recover cursor resources...
			cursor.dispose() ;
		}
		
		if( pathParts.peek().contains( "Participant" ) ) {
			log.debug( pathParts.peek() ) ;
		}
			
		//
		// We only need to check if the config file had something relevant...
		if( this.questionnaireFilters.containsKey( pathParts.peek() ) ) {			
			//
			// Build the relevant path...
			StringBuilder b = new StringBuilder() ;
			Iterator<String> i = pathParts.iterator() ;
			b.append( i.next() ) ;
			while( i.hasNext() ) {
				b.append( '/' ) 
				 .append( i.next() ) ;			 
			}
			String currentPath = b.toString() ;
			//
			// Does the config contain excludes...
			FilterType qt = this.questionnaireFilters.get( pathParts.peek() ) ;
			if( qt.sizeOfExcludeArray() == 0 ) {
				//
				// This looks like the edge case where a whole stage or entity is excluded...
				retCode = false ;
			}
			else {
				retCode = !currentPathExcluded( currentPath, qt.getQuestionnaire(), qt.getExcludeArray() ) ;
				if( retCode == true && qt.isSetAlternateName() ) {
					retCode = !currentPathExcluded( currentPath, qt.getAlternateName(), qt.getExcludeArray() ) ;
				}
			}
		}		
		return retCode ;		
	}
	
	private boolean currentPathExcluded( String currentPath, String questionnaire, ExcludeType[] excludePaths ) {
		if( currentPath.contains( "pat_email" ) ) {
			log.debug( "pat_email" ) ;
		}
		
		for( int i=0; i<excludePaths.length; i++ ) {
			if( excludePaths[i].sizeOfHintArray() == 0 ) {
				if( currentPath.equals( questionnaire + '/' + excludePaths[i].getName() ) ) {
					return true ;
				}
			}
			else {
				String[] hta = excludePaths[i].getHintArray() ;
				for( int j=0; j<hta.length; j++ ) {
					if( currentPath.equals( questionnaire + '/' + excludePaths[i].getName() + '/' + hta[j] ) ) {
						return true ;
					}
				} // end inner for loop
			}
			
		} // end outer for loop	
		return false ;
	}
	
	/**
	 * Manages the processing of questions.<br/>
	 * The following get ignored:
	 * <p><blockquote><pre>
	 *   1. Information only questions
	 *   2. Questions concerning the onset of symptoms
     * </pre></blockquote><p>
	 * NB: The reason for complex date/time questions associated with onset of symptoms being omitted 
	 *     from the ontology tree is that they are used as a start date for the observation_fact 
	 *     concerning symptoms when the PDO data is produced. <br/>
	 * <br/>
	 * With the exception of the above, the algorithm goes as follows:
	 * <p>
	 *   1. Questions which contain questions are called recursively until a variable array is encountered.
	 * <p>
	 *   2. Where there is more than one variable in a question, all variables are processed as distinct
	 *   children on the ontology tree.
	 * <p>
	 *   3. Where there is only one variable in a question, if the question and the variable have the same
	 *   name, then the question/variable combination is treated at least as a candidate for collapsing into 
	 *   one branch.  
     * <p>    
	 * @param question
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processQuestion( QuestionType question, Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processQuestion( QuestionType, Folder )" ) ;
		try {
			//
			// Let any user defined function have first crack...
			if( isUserExcluded( question ) ) {
				log.warn( "Question excluded by user defined function: " + question.getName() + " with label: " + question.getLabel() ) ;
				return ;
			}
			
			if( !isIncluded( question ) )
				return ;
			
			if( isInformationOnly( question ) ) {
				log.warn( "Information only question omitted: " + question.getName() + " with label: " + question.getLabel() ) ;
				return ;
			}

			Folder folder = parentFolder.addNewFolder() ;
			folder.setName( question.getName() ) ;
			if( question.getLabel() != null) {
				folder.setDescription( question.getLabel() ) ;
			}				
			if( question.sizeOfQuestionArray() > 0 ) {
				QuestionType[] qta = question.getQuestionArray() ;
				for( int i=0; i<qta.length; i++ ) {
					processQuestion( qta[i], folder ) ;
				}
			}
			else if( question.sizeOfVariableArray() > 1 ) {
				VariableType[] vta = question.getVariableArray() ;
				for( int i=0; i<vta.length; i++ ) {
					processVariable( vta[i], folder, false ) ;
				}
			}
			else {
				boolean collapsible = isCollapsible( question ) ;
				processVariable( question.getVariableArray( 0 ), folder, collapsible ) ;	
			}
		
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processQuestion( QuestionType, Folder )" ) ;
		}
	}
	
	private boolean isUserExcluded( XmlObject xo ) {
		if( this.userDefinedProcess != null ) {
			return this.userDefinedProcess.isExcluded( xo ) ;			
		}
		return false ;
	}
	

	/**
	 * An information only question is one which: <p>
	 * (a) contains no variables, or <p>
	 * (b) contains one variable with the same name as the question and that variable itself contains no restrictions
	 * (fixed enumerated concepts defined within the questionnaire) and no variables.
	 * 
	 * @param question
	 * @return true for information only questions, false otherwise.
	 */
	private boolean isInformationOnly( QuestionType question ) {
		if( question.sizeOfQuestionArray() == 0 ) {
			
			switch( question.sizeOfVariableArray() ) {
			case 0:
				return true ;
			case 1:
				if( question.getName().equals( question.getVariableArray( 0 ).getName() ) ) {
					VariableType variable = question.getVariableArray( 0 ) ;
					if( variable.sizeOfRestrictionArray() == 0 && variable.sizeOfVariableArray() == 0 ) {
						return true ;
					}
				}
				break;
			default:
				break;
			}
		}		
		return false ;
	}
	
	/**
	 * A question with a single variable with the same name as the question is
	 * considered to be "collapsible"; ie: the parent question and the child variable
	 * form one node in the ontology tree. 
	 * 
	 * @param question
	 * @return true if collapsible, false otherwise
	 */
	private boolean isCollapsible( QuestionType question ) {
		if( ( question.sizeOfVariableArray() == 1 )
			&&
		    ( question.getName().equals( question.getVariableArray( 0 ).getName() ) ) ) {
			return true ;				
		}		
		return false ;
	}
	
	/**
	 * A variable can be of three sorts:<p>
	 * 1. Contains other variables.<br/>
	 * 2. Contains restrictions. A restriction is a fixed enumeration designed into the 
	 * questionnaire. For example MANUAL or ELECTRONIC are restrictions placed on the form
	 * of consent.<br/>
	 * 3. A bottom leaf variable.<br/><p>
	 * For variables containing other variables, each variable is processed recursively,
	 * forming a folder on the tree, until a bottom leaf is reached.<p>
	 * For a variable containing restrictions, the restrictions are processed as
	 * bottom leaf enumerations.<p>
	 * For bottom leaf variables, a separate routine is called to complete the process.<p>
	 * 
	 * @param variable
	 * @param parentFolder
	 * @param collapsible Whether the question/variable is considered collapsible
	 *                    See section <a href="MetadataRefiner.html#isCollapsible">isCollapsible</a>
	 * @throws IOException
	 */
	private void processVariable( VariableType variable, Folder parentFolder, boolean collapsible ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processVariable( VariableType, Folder, collapsible )" ) ;
		Folder f = parentFolder ;
		try {
			//
			// Let any user defined function have first crack...
			if( isUserExcluded( variable ) ) {
				log.warn( "Variable excluded by user defined function: " + variable.getName() ) ;
				return ;
			}
			
			if( !isIncluded( variable ) ) 
				return ;
			
			if( variable.sizeOfVariableArray() > 0 ) {
				if( !collapsible ) {
					f = parentFolder.addNewFolder() ;
					f.setName( variable.getName() ) ;
					if( variable.getLabel() != null ) {
						f.setDescription( variable.getLabel() ) ;
					}					
				}	
				VariableType[] vta = variable.getVariableArray() ;
				for( int i=0; i<vta.length; i++ ) {
					processVariable( vta[i], f, false ) ;
				}
			}
			else if( variable.sizeOfRestrictionArray() > 0 ) {
				if( !collapsible ) {
					f = parentFolder.addNewFolder() ;
					f.setName( variable.getName() ) ;
					if( variable.getLabel() != null ) {
						f.setDescription( variable.getLabel() ) ;
					}
				}
				RestrictionType[] rta = variable.getRestrictionArray() ;
				for( int i=0; i<rta.length; i++ ) {
					//
					// This is an array of bottom leaf enumerations
					processRestriction( rta[i], f ) ;
				}
			}
			else {
				//
				// This is a bottom leaf...
				processVariable( variable, f ) ;
			}
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processVariable( VariableType, Folder, collapsible )" ) ;
		}
		
	}
	
	/**
	 * A restriction is a fixed enumeration designed into the questionnaire. 
	 * For example MANUAL or ELECTRONIC are restrictions placed on the form
	 * of consent. This routine processes each choice available within
	 * the restriction as a bottom leaf enumeration in the ontology tree.
	 * 
	 * @param restriction
	 * @param parentFolder
	 */
	private void processRestriction( RestrictionType restriction, Folder parentFolder ) {
		if( log.isTraceEnabled() ) enterTrace( "processRestriction( RestrictionType, Folder )" ) ;
		try {	
			String[] ena = restriction.getEnumArray() ;
			for( int i=0; i<ena.length; i++ ) {
				Variable v = parentFolder.addNewVariable() ;
				v.setName( ena[i] ) ;
				v.setType( Type.BOOLEAN ) ;
				v.setCode( formCode( restriction, v.getName(), Type.BOOLEAN ) ) ; 
			}
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processRestriction( RestrictionType, Folder )" ) ;
		}
	}
	
	/**
	 * Controls the processing of a bottom leaf variable.<br/>
	 * A bottom leaf variable is either: <br/>
	 * 1. entity related (eg: for Participant, ethnicity retrieved via the PMI Lookup), or <br/>
	 * 2. question related, or <br/>
	 * 3. neither.<p/>
	 * The only entity we have currently within Onyx is Participant. If this is an entity,
	 * the routine for processing Participant variables is called.<br/>
	 * Question-related variables can be complicated by the existence of sibling variables
	 * with differing characteristics. As examples of sibling variables, "Don't Know" and 
	 * "Prefer Not To Answer" are instances, but these are simple examples. Question-related
	 * variables possessing siblings are processed separately.<br/>
	 * Anything else is a sponge category of sorts and is a simple leaf on the ontology tree,
	 * and is processed accordingly.
	 * 
	 * @param variable
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processVariable( VariableType variable, Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processVariable( VariableType, Folder )" ) ;
		try {
			//
			// Let any user defined function have first crack...
			if( isUserExcluded( variable ) ) {
				log.warn( "Variable excluded by user defined function: " + variable.getName() ) ;
				return ;
			}
			
			if( !isIncluded( variable ) )
				return ;
			
			if( isEntityRelated( variable ) ) {
				processParticipantVariables( variable, parentFolder ) ;
			}
			else if( isQuestionRelated( variable ) && hasSiblingVariables( variable ) ) {
				processVariableHavingSiblings( variable, parentFolder ) ;					
			}
			else {
				Variable v = parentFolder.addNewVariable() ;
				v.setName( variable.getName() ) ;				
//				v.setType( Type.Enum.forString( variable.getType().toUpperCase() ) ) ;
				setType( v, variable ) ;
				v.setCode( formCode( variable, v.getName(), v.getType() ) ) ; 
				if( variable.getLabel() != null ) {
					v.setDescription( formDescription( variable, parentFolder ) ) ;
				}
			}
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processVariable( VariableType, Folder )" ) ;
		}
	}
	
	private void setType( Variable v, VariableType vt ) {
		if( vt.getType().toUpperCase().equals( "DATE" ) ) {
			v.setType( Type.DATETIME ) ;
			log.debug( "Setting type from DATE to DATETIME for " + vt.getName() ) ;
		}
		else {
			v.setType( Type.Enum.forString( vt.getType().toUpperCase() ) ) ;
		}
	}
	
	/**
	 * Forms a description of the variable from the parent folder and the variables label.
	 * 
	 * @param variable
	 * @param parentFolder
	 * @return A description of the variable.
	 */
	private String formDescription( VariableType variable, Folder parentFolder ) {
		return parentFolder.getDescription().trim() + ":" + variable.getLabel().trim() ;
	}
	
	
	/**
	 * Processes a single participant variable.<br/>
	 * At present there are two participant variables that are treated as generated enumerations: 
	 * Age and Ethnicity.
	 *  
	 * The rest are treated as normal variables. A generated enumeration
	 * is one <b>not</b> designed explicitly within the Onyx questionnaire. The generation
	 * process produces an XML file per enumerated concept. 
	 * 
	 * @param variable
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processParticipantVariables( VariableType variable, Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processParticipantVariables( VariableType, Folder )" ) ;
		
		try {
			if( log.isDebugEnabled() ) {
				if( variable.getName().contains( "age" ) ) {
					log.debug( "About to process: " + variable.getName() ) ;
				}
			}
			String variableName = variable.getName() ;
			//
			// For Age we want to produce an Age enumeration...
			EnumType ageEnumeration = this.enumerations.get( "AGE" ) ;
			String[] ageHints = ageEnumeration.getHintArray() ;
			//
			// The outer loop tests for variable inclusion...
			ageOuterLoop: for ( int i=0; i<ageHints.length; i++ ) {
				if( variableName.contains( ageHints[i] ) ) {
					String[] excludes = ageEnumeration.getExcludeArray() ;
					//
					// The inner loop tests for variable exclusion...
					for( int j=0; j<excludes.length; j++ ) {
						if( variableName.equals( excludes[j] ) ) {
							break ageOuterLoop ;
						}
					}
					//
					// First we need to produce an Age folder...
					Folder ageFolder = parentFolder.addNewFolder() ;
					ageFolder.setName( variableName ) ;
					ageFolder.setDescription( "Participant Age" ) ;
					ageFolder.setCode( formCodeStrategyOne( variable, variableName ) ) ;
					//
					// Then the enumeration...
					// (Not good technique. Need to refactor).
					// We modally switch off the fact that in this context there are no siblings...
					this.siblingHolder.siblingList.clear() ;
					_produceGroupedTree( ageFolder, ageEnumeration ) ;
					return ;
				}
			} // ageOuterLoop

			//
			// For ethnicity we want to produce an ethnic enumeration...
			if( variableName.equals( this.ethnicityVariableName ) ) {
				//
				// First we need to produce an ethnicity folder
				Folder eFolder = parentFolder.addNewFolder() ;
				eFolder.setName( this.ethnicityVariableName ) ;
				eFolder.setDescription( "Ethnic group" ) ;
				eFolder.setCode( formCodeStrategyOne( variable, this.ethnicityVariableName ) ) ;
				//
				// Then the enumeration...
				processEthnicEnumeration( eFolder ) ;
				return ;
			}
			//
			// For vital_status we want to produce a vital_status enumeration...
			if( variableName.equalsIgnoreCase( "vital_status" ) ) {
				//
				// First we need to produce a vital_status folder
				Folder eFolder = parentFolder.addNewFolder() ;
				eFolder.setName( "vital_status" ) ;
				eFolder.setDescription( "Vital status" ) ;
				eFolder.setCode( formCodeStrategyOne( variable, "vital_status" ) ) ;
				//
				// Then the enumeration...
				processVitalStatusEnumeration( eFolder ) ;
				return ;
			}
			//
			// All the rest we treat as straightforward variables...
			Variable v = parentFolder.addNewVariable() ;
			v.setName( variable.getName() ) ;				
//			v.setType( Type.Enum.forString( variable.getType().toUpperCase() ) ) ;
			setType( v, variable ) ;
			v.setCode( formCode( variable, v.getName(), v.getType() ) ) ; 
			if( variable.getLabel() != null ) {
				v.setDescription( formDescription( variable, parentFolder ) ) ;
			}
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processParticipantVariables( VariableType, Folder )" ) ;
		}
	}
	
	/**
	 * Processes a variable that possesses sibling variables. <p/>
	 * 
	 * Siblings can be related to each other in complex ways. <br/> 
	 * First of all this method deals with Standard Booleans, which are easy to deal with 
	 * and are standard leaf variables. <br/>
	 * Secondly, Standard Comments are simply ignored (Question: Is this OK?). <br/>
	 * Anything else requires discrimination, which means collecting siblings to do 
	 * some reasonably complex analysis of siblings.
	 * 
	 * @param variable
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processVariableHavingSiblings( VariableType variable, Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processVariableHavingSiblings( VariableType, Folder )" ) ;
		try {
			if( variable.getName().equals( "tobacco_cigquant" ) ) {
				log.debug( "tobacco_cigquant" ) ;
			}
			if( isStandardBoolean( variable ) ) {
				Variable v = parentFolder.addNewVariable() ;
				v.setName( variable.getName() ) ;				
				v.setType( Type.BOOLEAN ) ;
				v.setCode( formCode( variable, v.getName(), v.getType() ) ) ; 
				if( variable.getLabel() != null ) {
					v.setDescription( formDescription( variable, parentFolder ) ) ;
				}
			}
			else if( isStandardComment( variable ) ) {
				return ;
			}
			//
			// Take careful note here of the use of the utility SiblingHolder class.
			// I cannot see a better way of doing things at the moment than collecting
			// siblings together once a sibling has been encountered. But it does mean
			// the whole process gets switched modally into and out of sibling discrimination:
			// ie: once the siblingHolder is involved, we have the concept of a current
			// variable (the variable that triggers sibling processing), and the use
			// of the siblingHolder as relied-upon state for subsequent method calls.
			// Could do with a rethink.
			else if( this.siblingHolder.processed == false ) {
				discriminateSiblings( parentFolder ) ;
				this.siblingHolder.processed = true ;
			}			
		}
		finally {
			if( log.isTraceEnabled() ) exitTrace( "processVariableHavingSiblings( VariableType, Folder )" ) ;
		}
	}
	
	/**
	 * Having already eliminated the standard booleans (eg: PNA) and the unused comment, 
	 * the idea is to discriminate between:<p/>
	 * 
	 * (1) type-1. Collections of variables which are basically an enumeration of choices sitting 
	 *     alongside the standard booleans. These are enumerations defined within Onyx.<p/>
	 *     
	 * (2) type-2. Variables which are switched on by an OPEN boolean question and have a discrete value
	 *     which needs to be accommodated later in the process by an enumeration (eg: AGE).
	 *     Some of these are complex, like RECENT_TIME, which involves a number of fields.
	 *     A type-2 enumeration will be generated in a later process by a style sheet.<p/>
	 *     
	 * (3) type-3. Some variables which cannot be enumerated. 
	 *     An explicit date or variation on such is probably an observation_fact start date.
	 *     I need to report on these, as it is a difficult topic to deal with.<p/>
	 *     
	 *     This is a difficult area.
	 */
	private void discriminateSiblings( Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "discriminateSiblings" ) ;
		if( containsOpenQuestion() ) {
			// If there is an open question, 
			// we first try to find and process a type-2 enumeration.
			// If we cannot find one, then assume it is a continuous variable...
			// type-2...
			boolean generatedEnumProcessed = processGeneratedEnumeration( parentFolder ) ;
			//
			// type-3...
			if( !generatedEnumProcessed ) {
				processContinuousVariable( parentFolder ) ;
			}
		}
		else {
			//
			// type-1...
			processStandardEnumeration( parentFolder ) ;
		}
		if( log.isTraceEnabled() ) exitTrace( "discriminateSiblings" ) ;
	}
	
	/**
	 * Asks a series of questions concerning whether the given variable is a type
	 * of variable requiring generated enumeration. If an answer is positive, that
	 * type of generated enumeration is triggered. <p/>
	 * 
	 * <b>NB: Two points are of significance here.</b><p/>
	 * 1. The choice of what is an enumerated type and, alternatively, what should be covered
	 *    by a continuous variable is something that clinicians/bioinformatics staff should decide, 
	 *    weighing in the balance the ease of use of the user interface when building a query.<p/>
	 * 2. The choices (and the range of any enumeration) should be parameterized in some fashion 
	 *    to accommodate this choice without having recourse to code changes. <p/>
	 *    
	 * <b><em>This routine should be a focus of redevelopment.</em></b>
	 * 
	 * @param parentFolder
	 * @return true if an enumeration has been generated, false otherwise.
	 * @throws IOException
	 */
	private boolean processGeneratedEnumeration( Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processGeneratedEnumeration( Folder parentFolder )" ) ;
		boolean retValue = false ;
		parentFolder.setCode( formCodeForFolder() ) ;
		
		Iterator<EnumType> it = this.enumerations.values().iterator() ;
		while( it.hasNext() ) {
			EnumType et = it.next() ;
			
			if( searchForGeneratedEnumeration( et ) ) {
				
				if( et.getName().equals( "RECENT_TIME" ) ) {
					processRecentTimeEnumeration( parentFolder ) ;
					retValue = true ;
					break ;
				}				
				else if( et.getFirst() != null ) {
					
					if( et.getGroup() != null ) {
						_produceGroupedTree( parentFolder, et ) ;
						retValue = true ;
						break ;
					}
					else {
						_produceUnGroupedTree( parentFolder, et ) ;
						retValue = true ;
						break ;
					}
				}
			}
		}
		if( log.isTraceEnabled() ) exitTrace( "processGeneratedEnumeration( Folder parentFolder )" ) ;
		return retValue ;
	}
	
	/**
	 * Produces a complex "grouped" enumeration for recent time.
	 * 
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processRecentTimeEnumeration( Folder parentFolder ) throws IOException {
		//
		// First deal with the main refined metadata file...
		@SuppressWarnings("unused")
		Variable[] va = _processEnumeration( parentFolder ) ;
		//
		// The rest of the method covers generating the enumerations in a separate XML 
		// enumerations file...
		EnumeratedVariableDocument evDoc = EnumeratedVariableDocument.Factory.newInstance() ;
		RevEnumeratedVariable env = evDoc.addNewEnumeratedVariable() ;
		env.setName( parentFolder.getName() ) ;
		env.setCode( parentFolder.getCode() ) ;
		env.setType( RevType.Enum.forInt( Type.RECENTTIME.intValue() ) ) ;
		//
		// Path...
		String path =  buildPath( parentFolder ) ;
		env.setPath( path  ) ;
		
		String[] parts = path.split( "\\\\" ) ;
		env.setHlevel( Integer.toString( parts.length - 2 ) ) ;
		
		//
		// Format to make sure hours are always take up 2 character spaces...
		String hourFormat = "%2d" ;
		
		RevGroup rgToday = env.addNewGroup() ;
		rgToday.setName( "Today" ) ;
		rgToday.setDescription( rgToday.getName() ) ;		
		for( int i=0; i<24; i++ ) {
			RevGroup rgHour = rgToday.addNewGroup() ;
			rgHour.setName( "Hour " + String.format( hourFormat, i ) ) ;
			rgHour.setDescription( rgHour.getName() ) ;	
			for( int j=0; j<4; j++ ) {
				RevVariable rvMinute = rgHour.addNewVariable() ;				
				rvMinute.setName( "Min " + String.valueOf( j*15 )  ) ;
				rvMinute.setDescription( rvMinute.getName() ) ;
				rvMinute.setCode( env.getCode() + ":TODAY:" + String.valueOf( i ) + ':' + String.valueOf( j*15 ) ) ;
				if( rvMinute.getCode().length() > 50 ) {
					log.error( "Code length exceeds 50: " + rvMinute.getCode() ) ;
				}
			}
		}
		
		RevGroup rgYesterday = env.addNewGroup() ;
		rgYesterday.setName( "Yesterday" ) ;
		rgYesterday.setDescription( rgYesterday.getName() ) ;		
		for( int i=0; i<24; i++ ) {
			RevGroup rgHour = rgYesterday.addNewGroup() ;
			rgHour.setName( "Hour " + String.format( hourFormat, i ) ) ;
			rgHour.setDescription( rgHour.getName() ) ;	
			for( int j=0; j<4; j++ ) {
				RevVariable rvMinute = rgHour.addNewVariable() ;				
				rvMinute.setName( "Min " + String.valueOf( j*15 )  ) ;
				rvMinute.setDescription( rvMinute.getName() ) ;
				rvMinute.setCode( env.getCode() + ":YESTERDAY:" + String.valueOf( i ) + ':' + String.valueOf( j*15 ) ) ;
				if( rvMinute.getCode().length() > 50 ) {
					log.error( "Code length exceeds 50: " + rvMinute.getCode() ) ;
				}			
			}
		}
		
		RevVariable rv = env.addNewVariable() ;				
		rv.setName( "More than 24 hours" ) ;
		rv.setDescription( rv.getName() ) ;
		rv.setCode( env.getCode() + ":GT24H" ) ;
		//
		// Now save the file...
		saveEnumeratedDoc( evDoc ) ;
		evDoc = null ;
	}
	
	/**
	 * Produces an "ungrouped" enumeration for ethnic groups.
	 * 
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processEthnicEnumeration( Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processEthnicEnumeration" ) ;	
		
		EnumeratedVariableDocument evDoc = EnumeratedVariableDocument.Factory.newInstance() ;
		RevEnumeratedVariable env = evDoc.addNewEnumeratedVariable() ;
		env.setName( parentFolder.getName() ) ;
		env.setCode( parentFolder.getCode() ) ;
		env.setType( RevType.Enum.forInt( Type.GENERATED_ENUMERATION.intValue() ) ) ;
		//
		// Path...
		String path =  buildPath( parentFolder ) ;
		env.setPath( path  ) ;
		
		String[] parts = path.split( "\\\\" ) ;
		env.setHlevel( Integer.toString( parts.length - 2 ) ) ;
		
		for( int i=0; i<ethnicCodes.length; i++ ) {
			RevVariable rv = env.addNewVariable() ;				
			rv.setName( ethnicCodes[i].getName() ) ;
			rv.setDescription( ethnicCodes[i].getDescription() ) ;
			rv.setCode( env.getCode() + ':' + ethnicCodes[i].getStringValue() ) ;
			if( rv.getCode().length() > 50 ) {
				log.error( "Code length exceeds 50: " + rv.getCode() ) ;
			}
		}
				
		saveEnumeratedDoc( evDoc ) ;
		evDoc = null ;
		if( log.isTraceEnabled() ) exitTrace( "processEthnicEnumeration" ) ;
	}
	
	/**
	 * Produces an "ungrouped" enumeration for vital_status.
	 * 
	 * @param parentFolder
	 * @throws IOException
	 */
	private void processVitalStatusEnumeration( Folder parentFolder ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "processVitalStatusEnumeration" ) ;	
		
		EnumeratedVariableDocument evDoc = EnumeratedVariableDocument.Factory.newInstance() ;
		RevEnumeratedVariable env = evDoc.addNewEnumeratedVariable() ;
		env.setName( parentFolder.getName() ) ;
		env.setCode( parentFolder.getCode() ) ;
		env.setType( RevType.Enum.forInt( Type.VITALSTATUS.intValue() ) ) ;
		//
		// Path...
		String path =  buildPath( parentFolder ) ;
		env.setPath( path  ) ;
		
		String[] parts = path.split( "\\\\" ) ;
		env.setHlevel( Integer.toString( parts.length - 2 ) ) ;

		for( int i=0; i<VITAL_STATUS.length; i++ ) {
			RevVariable rv = env.addNewVariable() ;				
			rv.setName( VITAL_STATUS[i][1] ) ;
			rv.setDescription( rv.getName() ) ;
			rv.setCode( env.getCode() + ':' + VITAL_STATUS[i][0] ) ;
			if( rv.getCode().length() > 50 ) {
				log.error( "Code length exceeds 50: " + rv.getCode() ) ;
			}
		}
				
		saveEnumeratedDoc( evDoc ) ;
		evDoc = null ;
		if( log.isTraceEnabled() ) exitTrace( "processVitalStatusEnumeration" ) ;
	}
	
	
	/**
	 * The passed EnumType contains an array of hints and exclusions, and is examined against 
	 * the current variable to see whether the variable can be considered a member of the class
	 * of variables covered by the hints. For example, whether the variable covers
	 * cigarette smoking, or not.<p/>
	 * 
	 * The exclusions are then examined to see whether this variable is an exception to the rule.
	 * 
	 * Two things are taken into account in the two searches:<p/>
	 * 1. The variable's type.<br/>
	 * 2. The variable's name.<br/>
	 * <br/>
	 * If either of the above contains a hint from the EnumType inclusions, then the search 
	 * is provisionally satisfied. The the exclusions are searched for exceptions.
	 * 
	 * For example, "age" might be a hint, but would include "stage". "Stage" could be specifically
	 * dropped by having the word "stage" as an exclusion.
	 * 
	 * @param table
	 * @return true if the current variable is considered to be EnumType, false otherwise.
	 */
	private boolean searchForGeneratedEnumeration( final EnumType et ) {
		if( log.isTraceEnabled() ) enterTrace( "searchForGeneratedEnumeration()" ) ;
		boolean bType = false ;
		boolean bName = false ;
		boolean retCode = false ;
		try {
			//
			// Search the hints first...
			String[] hints = et.getHintArray() ;
			Iterator<VariableType> it = this.siblingHolder.siblingList.iterator() ;
			while( it.hasNext() ) {
				VariableType vt = it.next() ;
				for( int i=0; i<hints.length; i++ ) {
					if( vt.getType().contains( hints[i] ) ) {
						bType = true ;
					}
					else if( vt.getName().contains( hints[i] ) ) {
						bName = true ;
					}
				}
			}
			
			retCode = bType || bName ;
			//
			// If there are excludes, examine them... 
			String[] excludes = et.getExcludeArray() ;
			if( excludes.length > 0 ) {
				it = this.siblingHolder.siblingList.iterator() ;
				while( it.hasNext() ) {
					VariableType vt = it.next() ;
					for( int i=0; i<excludes.length; i++ ) {
						if( vt.getType().contains( excludes[i] ) ) {
							retCode = false ;
							break ;
						}
						else if( vt.getName().contains( excludes[i] ) ) {
							retCode = false ;
							break ;
						}
					}
				}
			}
		}
		finally {
			if( log.isDebugEnabled() ) {
				log.debug( "retcode: " + retCode ) ;
			}
			if( log.isTraceEnabled() ) exitTrace( "searchForGeneratedEnumeration()" ) ;
		}
						
		return retCode ;
	}
	
	/**
	 * Is the utility method for really producing a grouped enumeration. This is the real McCoy
	 * that every other routine wanting to produce a grouped enumeration invokes to do the work.
	 * 
	 * @param parentFolder
	 * @param type
	 * @param table
	 * @throws IOException
	 */
	private void _produceGroupedTree( Folder parentFolder, EnumType et ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "_produceGroupedTree( Folder parentFolder, EnumType et )" ) ;
		@SuppressWarnings("unused")
		Variable[] va =_processEnumeration( parentFolder ) ;
		EnumeratedVariableDocument evDoc = EnumeratedVariableDocument.Factory.newInstance() ;
		RevEnumeratedVariable env = evDoc.addNewEnumeratedVariable() ;
		env.setName( parentFolder.getName() ) ;
		env.setCode( parentFolder.getCode() ) ;		
		//
		// Path...
		String path =  buildPath( parentFolder ) ;
		env.setPath( path  ) ;
		
		String[] parts = path.split( "\\\\" ) ;
		env.setHlevel( Integer.toString( parts.length - 2 ) ) ;
		
		//
		//
		int first = Integer.valueOf( et.getFirst() ) ;
		int last = Integer.valueOf( et.getLast() ) ;
		int group = Integer.valueOf( et.getGroup() ) ;
		int greatestWidth = et.getLast().length() ;
		int numberGroups = ( last - first + 1 ) / group ;
				
		String format = "%" + String.valueOf( greatestWidth ) + "d" ;
		
		
		for( int i=0; i<numberGroups; i++ ) {
			RevGroup rg = env.addNewGroup() ;
			String name = String.format( format, first + i*group ) + " to " + String.valueOf( first +(i+1)*group - 1 ) ;
			rg.setName( name ) ;
			rg.setDescription( name ) ;
			int k = first + i*group ;
			for( int j=0; j<group; j++, k++ ) {
				RevVariable rv = rg.addNewVariable() ;				
				rv.setName( String.format( format, k ) ) ;
				rv.setDescription( rv.getName() ) ;
				rv.setCode( env.getCode() + ":" + rv.getName().trim() ) ;
			}			
		}		
		saveEnumeratedDoc( evDoc ) ;
		evDoc = null ;
		if( log.isTraceEnabled() ) exitTrace( "_produceGroupedTree( Folder parentFolder, EnumType et )" ) ;
	}
	
	/**
	 * Builds a path statement for a node in the ontology tree.
	 * 
	 * @param parentFolder
	 * @return A path statement describing a node in the i2b2 ontology tree.
	 *         (The path is in Microsoft format).
	 */
	private String buildPath( Folder parentFolder ) {
		//
		// First build a collection of ancestors...
		XmlCursor cursor = parentFolder.newCursor() ;		
		ArrayList<XmlObject> ancestors = new ArrayList<XmlObject>() ;
		try {
			ancestors.add( parentFolder ) ;
			while( cursor.toParent() ) {
				ancestors.add( (XmlObject)cursor.getObject() ) ;
			} ;
		}
		finally {
			cursor.dispose() ;
		}
		//
		// Use the collection to build a path.
		// Omits Participants and Participants/Admin, 
		// which as folders are not used.
		StringBuilder b = new StringBuilder() ;
		XmlObject[] anca = ancestors.toArray( new XmlObject[ ancestors.size() ] ) ;
		b.append( '\\' ) ;
		for( int i=anca.length-1; i>-1; i-- ) {
			if( anca[i] instanceof Folder ) {
				String folderName = ((Folder)anca[i]).getName() ;
				if( !folderName.equalsIgnoreCase( "Participants" ) 
					&&
					!folderName.equalsIgnoreCase( "Admin" ) ) {
					b.append( folderName ).append( '\\' ) ;
				}		
			}
			else if( anca[i] instanceof Container ) {
				b.append( ((Container)anca[i]).getName() ).append( '\\' ) ;
			}		
		}
		b.deleteCharAt( b.length()-1 ) ;
		String path = b.toString() ;
		
		return path ;
	}
	
	/**
	 * Is the utility method for really producing an ungrouped enumeration. This is the real McCoy
	 * that every other routine wanting to produce an ungrouped enumeration invokes to do the work.
	 * 
	 * @param parentFolder
	 * @param type
	 * @param range
	 * @throws IOException
	 */
	private void _produceUnGroupedTree( Folder parentFolder, EnumType et ) throws IOException {
		if( log.isTraceEnabled() ) enterTrace( "_produceUnGroupedTree( Folder parentFolder, EnumType et )" ) ;
		@SuppressWarnings("unused")
		Variable[] va =_processEnumeration( parentFolder ) ;
		//
		//
		EnumeratedVariableDocument evDoc = EnumeratedVariableDocument.Factory.newInstance() ;
		RevEnumeratedVariable env = evDoc.addNewEnumeratedVariable() ;
		env.setName( parentFolder.getName() ) ;
		env.setCode( parentFolder.getCode() ) ;
		//
		// Path...
		String path = buildPath( parentFolder ) ;
		log.debug( "built path: " + path ) ;
		env.setPath( path ) ;

		String[] parts = path.split( "\\\\" ) ;
		env.setHlevel( Integer.toString( parts.length - 2 ) ) ;
	
		String greatestWidth = Integer.toString( et.getLast().length() ) ;
		
		String format = "%" + greatestWidth + "d";
		int j = Integer.valueOf( et.getFirst() ) ;
		int k = Integer.valueOf( et.getLast() ) - j + 1 ;
		for( int i=0; i<k; i++, j++ ) {
			RevVariable rv = env.addNewVariable() ;				
			rv.setName( String.format( format, j ) ) ;
			rv.setDescription( rv.getName() ) ;
			rv.setCode( env.getCode() + ":" + rv.getName().trim() ) ;		
		}	
		saveEnumeratedDoc( evDoc ) ;
		evDoc = null ;
		if( log.isTraceEnabled() ) exitTrace( "_produceUnGroupedTree( Folder parentFolder, EnumType et )" ) ;
	}
	
	/**
     * The utility method for producing the core enumeration within the main refined metadata file. 
     * This is the real McCoy that other routines wanting to produce an enumerations invoke to do work
     * within the main file.
	 * 
	 * @param parentFolder
	 * @param typeEnum
	 * @return
	 */
	private Variable[] _processEnumeration( Folder parentFolder ) {
		Iterator<VariableType> it = this.siblingHolder.siblingList.iterator() ;
		ArrayList<Variable> alv = new ArrayList<Variable>() ;
		while( it.hasNext() ) {
			VariableType vt = it.next() ;
			//
			// This bit drops the open question variable...
			if( vt.getType().equalsIgnoreCase( "boolean" ) ) {
				if( !isStandardBoolean( vt ) ) {
					continue ;
				}
			}
			Variable v = parentFolder.addNewVariable() ;
			v.setName( vt.getName() ) ;				
			v.setType( Type.GENERATED_ENUMERATION ) ;
			v.setCode( formCode( vt, v.getName(), v.getType() ) ) ;
			if( vt.getLabel() != null ) {
				v.setDescription( vt.getLabel() ) ;
			}
			alv.add( v ) ;
		}
		return alv.toArray( new Variable[ alv.size()] ) ;
	}
	
//	/**
//	 * @return true if the current variable involves measuring Age, false otherwise.
//	 */
//	private boolean isAgeEnumeration() {
//		return _searchStandardTable( STANDARD_AGE ) ;
//	}
	
	/**
	 * Continuous variable may be misnamed, but the idea is of a variable that is not enumerated.
	 * 
	 * @param parentFolder
	 */
	private void processContinuousVariable( Folder parentFolder ) {
		Iterator<VariableType> it = this.siblingHolder.siblingList.iterator() ;
		while( it.hasNext() ) {
			VariableType vt = it.next() ;
			if( vt.getType().equalsIgnoreCase( "boolean" ) ) {
				if( !isStandardBoolean( vt ) ) {
					continue ;
				}
			}
			Variable v = parentFolder.addNewVariable() ;
			v.setName( vt.getName() ) ;				
//			v.setType( Type.Enum.forString( vt.getType().toUpperCase() ) ) ;
			setType( v, vt ) ;
			v.setCode( formCode( vt, v.getName(), v.getType() ) ) ;
			if( vt.getLabel() != null ) {
				v.setDescription( vt.getLabel() ) ;
			}
		}
	}
	
	/**
	 * Collections of variables which are basically an enumeration of choices sitting 
	 * alongside the standard booleans. These are enumerations defined within Onyx.
	 *     
	 * @param parentFolder
	 */
	private void processStandardEnumeration( Folder parentFolder ) {
		Iterator<VariableType> it = this.siblingHolder.siblingList.iterator() ;
		while( it.hasNext() ) {
			VariableType vt = it.next() ;
			Variable v = parentFolder.addNewVariable() ;
			v.setName( vt.getName() ) ;				
//			v.setType( Type.Enum.forString( vt.getType().toUpperCase() ) ) ;
			setType( v, vt ) ;
			v.setCode( formCode( vt, v.getName(), v.getType() ) ) ;
			if( vt.getLabel() != null ) {
				v.setDescription( vt.getLabel() ) ;
			}
		}
	}
	
	/**
	 * An OPEN question is indicated when a collection of sibling variables
	 * contains at least one which is a continuous variable (a measure) and one which
	 * is a non-standard boolean.
	 * 
	 * @return true if sibling variables are within the context of an OPEN question
	 */
	private boolean containsOpenQuestion() {
		Iterator<VariableType> it = this.siblingHolder.siblingList.iterator() ;
		boolean continuousVariablePresent = false ;
		boolean nonStandardBooleanPresent = false ;
		while( it.hasNext() ) {
			VariableType vt = it.next() ;
			if( isOnyxContinousType( vt ) ) {
				continuousVariablePresent = true ;
				continue ;
			}
			if( vt.getType().equalsIgnoreCase( "boolean" ) ) {
				if( !isStandardBoolean( vt ) ) {
					nonStandardBooleanPresent = true ;
				}
			}
		}		
		return (continuousVariablePresent && nonStandardBooleanPresent ) ;
	}
	
	/**
	 * An Onyx continuous variable is either "DATETIME", "DECIMAL", "INTEGER",  or "TEXT"
	 * 
	 * @param vt
	 * @return true if the given variable is a Onyx continuous variable, false otherwise.
	 */
	private boolean isOnyxContinousType( VariableType vt ) {
		String type = vt.getType() ;
		for( int i=0; i< ONYX_CONTINUOUS_TYPES.length; i++ ) {
			if( type.equalsIgnoreCase( ONYX_CONTINUOUS_TYPES[i] ) ) {
				return true ;
			}
		}
		return false ;
	}
	
	/**
	 * A standard boolean is, for example, "Y", "N", "PNA", "DK", "Not_Recorded",
	 * and so on. 
	 * 
	 * @param variable
	 * @return true if the variable is a standard boolean, false otherwise.
	 */
	private boolean isStandardBoolean( VariableType variable ) {
		String name = variable.getName() ;
		for( int i=0; i<standardBooleans.length; i++ ) {
			if( name.equalsIgnoreCase( standardBooleans[i] ) ) {
				return true ;
			}
		}
		return false ;
	}
	
	/**
	 * Almost every question in the Onyx questionnaire has an associated
	 * comment field. The name of the variable is "comment".
	 * 
	 * @param variable
	 * @return true if the variable is a standard comment, false otherwise.
	 */
	private boolean isStandardComment( VariableType variable ) {
		if( variable.getName().equals( "comment" ) ) {
			return true ;
		}
		return false ;
	}
	
	/**
	 * A variable is question related if it is held within a question.
	 * This routine works its way up the tree, looking at the parent
	 * and ancestors of the variable node. If it finds a QuestionType,
	 * then the variable is question related.
	 * 
	 * @param variable
	 * @return true if the variable is question related, false otherwise
	 */
	private boolean isQuestionRelated( VariableType variable ) {
		XmlCursor cursor = variable.newCursor() ;
		try {
			while( cursor.toParent() ) {
				XmlObject xo = cursor.getObject() ;
				if( xo.schemaType() == QuestionType.type ) {
					return true ;
				}
			}
			return false ;
		}
		finally {
			cursor.dispose() ;
		}
	}
	
	/**
	 * A variable is enitity related if it is held within an entity.
	 * For example a variable relating to a Participant.
	 * This routine works its way up the tree, looking at the parent
	 * and ancestors of the variable node. If it finds an EntityType,
	 * then the variable is entitiy related.
	 * 
	 * @param variable
	 * @return true if the variable is entity related, false otherwise.
	 */
	private boolean isEntityRelated( VariableType variable ) {
		XmlCursor cursor = variable.newCursor() ;
		try {
			while( cursor.toParent() ) {
				XmlObject xo = cursor.getObject() ;
				if( xo.schemaType() == EntityType.type ) {
					if( log.isDebugEnabled() ) {
						log.debug( "Entity related: " + variable.getName() ) ;
					}
					return true ;
				}
			}
			return false ;
		}
		finally {
			cursor.dispose() ;
		}
	}
	
	/**
	 * A variable has sibling variables if - within the DOM - it has
	 * sibling nodes which are also of VariableType. As a side effect,
	 * if found this routine primes the sibling process.
	 * 
	 * @param variable
	 * @return true if there are sibling variables for the given variable, false otherwise.
	 */
	private boolean hasSiblingVariables( VariableType variable ) {
		if( log.isTraceEnabled() ) enterTrace( "hasSiblingVariables()" ) ;
		if( log.isDebugEnabled() ) {
			log.debug( "variable: " + variable.getName() ) ;
		}
		XmlCursor cursor = variable.newCursor() ;
		try {
			while( cursor.toPrevSibling() ) {}
			if( cursor.getObject() instanceof VariableType ) {
				primeSiblingsProcess( variable ) ;
				return true ;
			}
			while( cursor.toNextSibling() ) {
				if( cursor.getObject() instanceof VariableType ) {
					primeSiblingsProcess( variable ) ;
					return true ;
				}
			}
			return false ;
		}
		finally {
			cursor.dispose() ;
			if( log.isTraceEnabled() ) exitTrace( "hasSiblingVariables()" ) ;
		}
	}
	
	/**
	 * Given a variable, priming the sibling process initializes the sibling holder,
	 * filling it with a collection of sibling variables and recording the parent of the siblings.
	 * Standard booleans and the standard comment get ignored. 
	 * 
	 * @param vt
	 */
	private void primeSiblingsProcess( VariableType vt ) {
		if( log.isTraceEnabled() ) enterTrace( "primeSiblingsProcess()" ) ;
		XmlCursor cursor = vt.newCursor() ;
		try {
			cursor.push() ;
			cursor.toParent() ;
			if( cursor.getObject() != this.siblingHolder.parent ) {
				if( log.isDebugEnabled() ) {
					log.debug( "priming siblingHolder" ) ;
				}
				this.siblingHolder.processed = false ;
				this.siblingHolder.parent = (VariableType)cursor.getObject() ;
				this.siblingHolder.siblingList.clear() ;
				cursor.pop() ;
				while( cursor.toPrevSibling() ) {}
				recordIfRelevant( cursor.getObject() ) ;
				while( cursor.toNextSibling() ) {
					recordIfRelevant( cursor.getObject() ) ;
				}			
			}
		}
		finally {
			cursor.dispose() ;
			if( log.isTraceEnabled() ) exitTrace( "primeSiblingsProcess()" ) ;
		}
	}
	
	/**
	 * Adds the given object to the sibling collection within the sibling holder
	 * if the object is a variable, but not a standard boolean or a standard comment.
	 * 
	 * @param obj
	 */
	private void recordIfRelevant( Object obj ) {
		if( obj instanceof VariableType == false ) {
			return ;
		}
		VariableType vt = (VariableType)obj ;
		if( isStandardBoolean( vt ) || isStandardComment( vt ) ) {
			return ;
		}
		this.siblingHolder.siblingList.add( vt ) ;
	}
	
	/**
	 * Forms the ontology code for this variable. For a formal ontology this would be
	 * the SNOMED or LOINC code. This routine is a makeshift whilst we work out the
	 * formal codes for Onyx variables.
	 * 
	 * @param xo	The leaf object
	 * @param name	The name of the object
	 * @param typeEnum	The type of the object
	 * @return The code as a string (needs to be 50 characters or less for i2b2).
	 */
	private String formCode( XmlObject xo, String name, Type.Enum typeEnum ) {
		if( typeEnum == null ) {
			log.debug( "typeEnum is null!" ) ;
		}
		switch ( typeEnum.intValue() ) {
		//
		// These are all generated enumerations...
		case Type.INT_GENERATED_ENUMERATION:
		//
		// These types are deprecated in favour of INT_GENERATED_ENUMERATION...
		case Type.INT_AGE:
		case Type.INT_BICEPS:
		case Type.INT_DIASTOLICBP:
		case Type.INT_HEIGHT:
		case Type.INT_HIPS:
		case Type.INT_RECENTTIME:
		case Type.INT_SMALLNUMBER:
		case Type.INT_SUBSCAPULAR:
		case Type.INT_SUPRAILIAC:
		case Type.INT_SYSTOLICBP:
		case Type.INT_TRICEPS:
		case Type.INT_WAIST:
		case Type.INT_WEIGHT:
		case Type.INT_YEAR:
			return formCodeStrategyTwo( xo, name, typeEnum ) ;
		//
		// Everything other than generated enumerations...
		default:
			return formCodeStrategyOne( xo, name ) ;
		}		
	}
	
	/**
	 * Strategy one is the strategy for forming codes for non-enumerated variables.
	 * 
	 * @param xo	The leaf object
	 * @param name	The name of the object
	 * @return		The code as a string
	 */
	private String formCodeStrategyOne( XmlObject xo, String name ) {
		String code = null ;
		StringBuilder builder = new StringBuilder( 100 ) ;
		XmlObject parent = getParent( xo ) ;
		builder.append( this.configDoc.getOnyxExportConfig().getCodePrefix() ) 
		       .append( getName( parent ) )
		       .append( '.' )
		       .append( name ) ; 
		code = builder.toString() ;
		
		if( code.length() > 50 ) {
			code = rationalizeCode( code ) ; 
		}
		
		if( codeContainer.contains( code ) ) {
			builder = new StringBuilder( 100 ) ;
			parent = getParent( xo ) ;
			builder.append( this.configDoc.getOnyxExportConfig().getCodePrefix() ) 
			       .append( getHigherQualifier( parent ) )
			       .append( '.' )
			       .append( getName( parent ) )
			       .append( '.' )
			       .append( name ) ; 
			code = builder.toString() ;
		}	
		
		if( code.length() > 50 ) {
			code = rationalizeCode( code ) ; 
		}
			
		if( codeContainer.contains( code ) ) {
			log.error( "Code name clash: " + code ) ;
		}
		if( code.length() > 50 ) {
			log.error( "Code exceeds 50 characters in length: " + code ) ;
		}
		
		codeContainer.add( code ) ;
		return code ;
	}
	
	/**
	 * Strategy two is the strategy for forming partial codes for enumerated variables.
	 * Why partial? The code returned here is the "root" of the code, which would require
	 * augmentation for every enumerated value...<p/>
	 * 
	 * For example, the following is a complete code for an enumeration on AGE 51:
	 * <p/>
	 * CBO:father_hf_age_cat:51
	 * <p/>
	 * This routine would return:
	 * <p/>
	 * CBO:father_hf_age_cat
	 * 
	 * @param xo	The leaf object
	 * @param name	The name of the object
	 * @param typeEnum The type of the object
	 * @return		The code as a string
	 */
	private String formCodeStrategyTwo( XmlObject xo, String name, Type.Enum typeEnum ) {
		String code = null ;
		StringBuilder builder = new StringBuilder( 100 ) ;
		XmlObject parent = getParent( xo ) ;
		builder.append( this.configDoc.getOnyxExportConfig().getCodePrefix() ) 
		       .append( name )  ;
		code = builder.toString() ;
		
		if( code.length() > 40 ) {
			code = rationalizeCode( code ) ; 
		}
		
		if( codeContainer.contains( code ) ) {
			builder = new StringBuilder( 100 ) ;
			parent = getParent( xo ) ;
			builder.append( this.configDoc.getOnyxExportConfig().getCodePrefix() ) 
			       .append( getHigherQualifier( parent ) )
			       .append( '.' )
			       .append( name ) ;
			code = builder.toString() ;
		}	
		
		if( code.length() > 40 ) {
			code = rationalizeCode( code ) ; 
		}
			
		if( codeContainer.contains( code ) ) {
			log.error( "Code name clash: " + code ) ;
		}
		if( code.length() > 40 ) {
			log.error( "Code exceeds 40 characters in length: " + code ) ;
		}
		
		codeContainer.add( code ) ;
		return code ;
	}
	
	/**
	 * @return an ontology code for the current variable's parent in string form
	 */
	private String formCodeForFolder() {
		String code = null ;
		StringBuilder builder = new StringBuilder( 100 ) ;
		
		VariableType vt = this.siblingHolder.parent ;
		
		builder.append( this.configDoc.getOnyxExportConfig().getCodePrefix() ) 
		       .append( vt.getName() )  ;
		code = builder.toString() ;
		
		if( code.length() > 40 ) {
			code = rationalizeCode( code ) ; 
		}
		
		if( codeContainer.contains( code ) ) {
			builder = new StringBuilder( 100 ) ;
			XmlObject parent = getParent( vt ) ;
			builder.append( this.configDoc.getOnyxExportConfig().getCodePrefix() ) 
			       .append( getHigherQualifier( parent ) )
			       .append( '.' )
			       .append( vt.getName() ) ;
			code = builder.toString() ;
		}	
		
		if( code.length() > 40 ) {
			code = rationalizeCode( code ) ; 
		}
			
		if( codeContainer.contains( code ) ) {
			log.error( "Code name clash: " + code ) ;
		}
		if( code.length() > 40 ) {
			log.error( "Code exceeds 40 characters in length: " + code ) ;
		}		
		codeContainer.add( code ) ;
		return code ;
	}
	
	/**
	 * The makeshift ontology codes must be less than a certain length (overall 50 characters or less).
	 * This routine attempts a predictable/deterministic reduction in characters.
	 * 
	 * @param code
	 * @return rationalized code
	 */
	private String rationalizeCode( String code ) {
		String[] p1 = code.split( "\\." ) ;
		String[] p2 = new String[p1.length] ;
		
		for( int i=0; i<p1.length; i++ ) {
			p2[i] =  p1[i].replaceAll( "[aeiou]", "" ) ;			
		}
	
		StringBuilder b = new StringBuilder() ;
		//
		// First try contracting all but the last 2 parts...
		for( int i=0; i<p1.length; i++ ) {
			if( i < p1.length-2 ) {
				b.append( p2[i] ).append( '.' ) ; 
			}
			else {
				b.append( p1[i] ).append( '.' ) ;
			}			
		}
		b.deleteCharAt( b.length()-1 ) ;
		//
		// If that doesn't work, contract all parts...
		if( b.length() > 40 ) {
			b = new StringBuilder() ;
			for( int i=0; i<p2.length; i++ ) {
				b.append( p2[i] ).append( '.' ) ; 
			}
			b.deleteCharAt( b.length()-1 ) ;
		}				
		return b.toString() ;
	}
	
	/**
	 * Makeshift utility to extract some higher level qualifier for a code.
	 * Maybe required to ensure a variable code is unique.
	 * 
	 * @param xo Bottom leaf object.
	 * @return A code qualifier based upon Stage Name or Entity Name.
	 */
	private String getHigherQualifier( XmlObject xo ) {
		XmlCursor cursor = xo.newCursor() ;
		String name = null ;
		try {
			while( cursor.toParent() ) {
				XmlObject pxo = cursor.getObject() ;
				if( pxo instanceof StageType ) {
					name = ((StageType)pxo).getName() ;
				}
				else if( pxo instanceof EntityType ) {
					name = ((EntityType)pxo).getName() ;
				}
			}
			if( name == null ) {
				return "" ;
			}
			StringBuilder b = new StringBuilder() ;
			for( int i=0; i<name.length(); i++ ) {
				if( Character.isUpperCase( name.charAt( i ) ) ) {
					b.append( name.charAt(i) ) ;
				}
			}
			if( b.length() == 0 ) {
				return new String( new char[] { Character.toUpperCase( name.charAt( 0 ) ) } ) ;
			}
			return b.toString() ;
		}
		finally {
			cursor.dispose() ;
		}
	}
	
	/**
	 * Utility method for finding the parent of a given object within the DOM.
	 * 
	 * @param xo The given object
	 * @return The parent of the given object
	 */
	private XmlObject getParent( XmlObject xo ) {
		XmlCursor cursor = xo.newCursor() ;
		try {
			if( cursor.toParent() ) {
				return cursor.getObject() ;
			}
			return null ;
		}
		finally {
			cursor.dispose() ;
		}
	}
	
	/**
	 * Utility method to return the name of a given object.
	 * If a name cannot be found, returns null and logs an error.
	 * 
	 * @param xo
	 * @return The name of the given object
	 */
	private String getName( XmlObject xo ) {
		if( xo instanceof VariableType ) {
			return ((VariableType)xo).getName() ;
		}
		else if( xo instanceof QuestionType ) {
			return ((QuestionType)xo).getName() ;
		}
		else if( xo instanceof SectionType ) {
			return ((SectionType)xo).getName() ;
		}
		else if( xo instanceof StageType ) {
			return ((StageType)xo).getName() ;
		}
		else if( xo instanceof EntityType ) {
			return ((EntityType)xo).getName() ;
		}
		else if( xo instanceof SourceType ) {
			return ((SourceType)xo).getName() ;
		}
		log.error( "Method getName() misbehaves for object of type " + xo.getClass().getName() + " ***" ) ;
		return null ;
	}
	
	/**
	 * Pretty prints the main refined metadata file to standard out.
	 */
	public void print() {
		XmlOptions opts = getSaveOptions() ;
		System.out.println( containerDoc.xmlText(opts) ) ;
	}
	
	/**
	 * Saves the main refined metadata file to the given path.
	 * 
	 * @param fullPath
	 * @throws IOException
	 */
	public void save( String fullPath ) throws IOException {
		XmlOptions opts = getSaveOptions() ;
		containerDoc.save( new File( fullPath ), opts ) ;
	}
	
	/**
	 * Saves the given document as a file to the enumerated concepts directory.
	 * 
	 * @param doc
	 * @throws IOException
	 */
	public void saveEnumeratedDoc( EnumeratedVariableDocument doc ) throws IOException {
		String filePath = outEnumDirectoryPath + File.separator + doc.getEnumeratedVariable().getName() + ".xml" ;
		XmlOptions opts = getSaveOptions() ;
		File enumFile = new File( filePath ) ;
		
		if( enumFile.exists() ) {
			throw new IOException( "Enumerated file already exists: " + enumFile.getName() ) ;
		}
		doc.save( enumFile, opts ) ;
	}
	
    /**
     * Returns the <code>XmlOptions</code> required to produce
     * a text representation of the emitted XML.
     * 
     * @param prettyPrint
     * @return XmlOptions
     */
    private XmlOptions getSaveOptions() {
        XmlOptions opts = new XmlOptions();
        opts.setSaveOuter() ;
        opts.setSaveNamespacesFirst() ;
        opts.setSaveAggressiveNamespaces() ;   
        opts.setSavePrettyPrint() ;
        opts.setSavePrettyPrintIndent( 3 ) ; 
        return opts ;
    }
	
	/**
	 * Utility routine to enter a structured message in the trace log that the given method 
	 * has been entered. Almost essential for syntax debugging.
	 * 
	 * @param entry: the name of the method entered
	 */
	public static void enterTrace( String entry ) {
		log.trace( getIndent().toString() + "enter: " + entry ) ;
		indentPlus() ;
	}

    /**
     * Utility routine to enter a structured message in the trace log that the given method 
	 * has been exited. Almost essential for syntax debugging.
	 * 
     * @param entry: the name of the method exited
     */
    public static void exitTrace( String entry ) {
    	indentMinus() ;
		log.trace( getIndent().toString() + "exit : " + entry ) ;
	}
	
    /**
     * Utility method used to maintain the structured trace log.
     */
    public static void indentPlus() {
		getIndent().append( ' ' ) ;
	}
	
    /**
     * Utility method used to maintain the structured trace log.
     */
    public static void indentMinus() {
        if( logIndent.length() > 0 ) {
            getIndent().deleteCharAt( logIndent.length()-1 ) ;
        }
	}
	
    /**
     * Utility method used for indenting the structured trace log.
     */
    public static StringBuffer getIndent() {
	    if( logIndent == null ) {
	       logIndent = new StringBuffer() ;	
	    }
	    return logIndent ;	
	}
    
    /**
     * Utility method to resent the trace function's indentation.
     */
    @SuppressWarnings("unused")
	private static void resetIndent() {
        if( logIndent != null ) { 
            if( logIndent.length() > 0 ) {
               logIndent.delete( 0, logIndent.length() )  ;
            }
        }   
    }
    
    public void initialize() throws MetadataRefinerException {
    	//
		// Vet input directory for existence...
		this.inputDirectory = new File( inDirectoryPath ) ;
		if( !inputDirectory.exists() ) {
			throw new MetadataRefinerException( "Input directory does not exist: [" + inDirectoryPath + "]" ) ; 
		}		
		
		//
		// Bail out if no files exist...
		this.fileNames = inputDirectory.list() ;
		if( fileNames.length == 0 ) {
			throw new MetadataRefinerException( "Input directory is empty." ) ;
		}
		
		//
		// Parse the config file into XmlBeans...
		try {
			File configFile = new File( configFilePath ) ;
			configDoc = OnyxExportConfigDocument.Factory.parse( configFile ) ;
			ontologyPhase = configDoc.getOnyxExportConfig().getOntologyPhase() ;
		}
		catch( Exception ex ) {
			throw new MetadataRefinerException( "Configuration file is invalid.", ex ) ;
		}
		
		//
		// Retrieve data from the config file...
		
		//
		// Give the ontology a name...
		this.containerDoc.addNewContainer().setName( configDoc.getOnyxExportConfig().getOntologyPhase().getOntologyRootName() ) ;
		
		//
		// Ethnic codes plus the questionnaire variable name...
		this.ethnicityVariableName = configDoc.getOnyxExportConfig().getOntologyPhase().getEthnicity().getVariableName() ;
		this.ethnicCodes = configDoc.getOnyxExportConfig().getOntologyPhase().getEthnicity().getCodeArray() ;
		//
		// Standard boolean values assumed within the questionnaire...
		this.standardBooleans = configDoc.getOnyxExportConfig().getOntologyPhase().getStandardBooleans().getValueArray() ;	
		//
		// The (sub-)questionnaires to be excluded...
		FilterType[] qta = configDoc.getOnyxExportConfig().getOntologyPhase().getQuestionnaires().getFilterArray() ;
		//Jeff
		this.questionnaireFilters = new HashMap<String, FilterType>() ;
		for( FilterType filter : qta ) {
			questionnaireFilters.put( filter.getQuestionnaire(), filter ) ;
			//
			// Some questionnaires may have an "alternative" internal/external name
			// (eg: Participants and Participant)...
			if( filter.isSetAlternateName() ) {
				questionnaireFilters.put( filter.getAlternateName(), filter ) ;
			}
		}
		if( log.isDebugEnabled() ) {
			Set<String> keys = questionnaireFilters.keySet() ;
			Iterator it = keys.iterator() ;
			while( it.hasNext() ) {
				log.debug( it.next() ) ;
			}
		}

		//
		// All the enumerated types, which are place in a linked hash map...
		EnumType[] ets = configDoc.getOnyxExportConfig().getOntologyPhase().getEnumeratedConcepts().getEnumArray() ;
		this.enumerations = new LinkedHashMap<String,EnumType>() ;
		for( EnumType enumType : ets ) {
			this.enumerations.put( enumType.getName(), enumType ) ;
		}
				
		//
		// If provided, vet output directories for non existence...
		this.outputDirectory = null ;
		if( outRefDirectoryPath != null ) {
			outputDirectory = new File( outRefDirectoryPath ) ;
			if( outputDirectory.exists() ) {
				throw new MetadataRefinerException( "Output refined directory already exists: [" + outRefDirectoryPath + "]" ) ;
			}
			outputDirectory.mkdirs() ;
		}
		if( outEnumDirectoryPath != null ) {
			outputDirectory = new File( outEnumDirectoryPath ) ;
			if( outputDirectory.exists() ) {
				throw new MetadataRefinerException( "Output enum directory already exists: [" + outEnumDirectoryPath + "]" ) ;
			}
			outputDirectory.mkdirs() ;
		}
		
		if( this.ontologyPhase.isSetUserDefinedProcedure() ) {
			
			String fullPackageName = null ;
			try {
				fullPackageName = this.ontologyPhase.getUserDefinedProcedure().trim() ;
				Class clzz =  Class.forName( fullPackageName ) ;		
				this.userDefinedProcess = (IExport2Ontology)clzz.newInstance() ; 
			}
			catch( Exception ex ) {
				log.error( "Could not instantiate user defined procedure: " + fullPackageName, ex ) ;
			}
			
		}
				
    }
    
    /**
	 * @return the inDirectoryPath
	 */
	public String getInDirectoryPath() {
		return inDirectoryPath;
	}

	/**
	 * @param inDirectoryPath the inDirectoryPath to set
	 */
	public void setInDirectoryPath(String inDirectoryPath) {
		this.inDirectoryPath = inDirectoryPath;
	}

	/**
	 * @return the configFilePath
	 */
	public String getConfigFilePath() {
		return configFilePath;
	}

	/**
	 * @param configFilePath the configFilePath to set
	 */
	public void setConfigFilePath(String configFilePath) {
		this.configFilePath = configFilePath;
	}

	/**
	 * @return the outRefDirectoryPath
	 */
	public String getOutRefDirectoryPath() {
		return outRefDirectoryPath;
	}

	/**
	 * @param outRefDirectoryPath the outRefDirectoryPath to set
	 */
	public void setOutRefDirectoryPath(String outRefDirectoryPath) {
		this.outRefDirectoryPath = outRefDirectoryPath;
	}

	/**
	 * @return the outEnumDirectoryPath
	 */
	public String getOutEnumDirectoryPath() {
		return outEnumDirectoryPath;
	}

	/**
	 * @param outEnumDirectoryPath the outEnumDirectoryPath to set
	 */
	public void setOutEnumDirectoryPath(String outEnumDirectoryPath) {
		this.outEnumDirectoryPath = outEnumDirectoryPath;
	}

	/**
	 * @return the mainFileName
	 */
	public String getMainFileName() {
		return mainFileName;
	}

	/**
	 * @param mainFileName the mainFileName to set
	 */
	public void setMainFileName(String mainFileName) {
		this.mainFileName = mainFileName;
	}

	/**
     * Retrieves the command line arguments, with very basic checking.
     * 
     * @param args
     * @return true if all mandatory arguments were supplied, false otherwise
     */
    private boolean retrieveArgs( String[] args ) {
        boolean retVal = false ;
        if( args != null && args.length > 0 ) {
            
            for( int i=0; i<args.length; i++ ) {
                
            	if( args[i].startsWith( "-input=" ) ) { 
                    this.inDirectoryPath = args[i].substring(7) ;
                }
                else if( args[i].startsWith( "-i=" ) ) { 
                	this.inDirectoryPath = args[i].substring(3) ;
                }
                else if( args[i].startsWith( "-config=" ) ) { 
                	this.configFilePath = args[i].substring(8) ;
                }
                else if( args[i].startsWith( "-c=" ) ) { 
                	this.configFilePath = args[i].substring(3) ;
                }
                else if( args[i].startsWith( "-refine=" ) ) { 
                	this.outRefDirectoryPath = args[i].substring(8) ;
                }
                else if( args[i].startsWith( "-r=" ) ) { 
                	this.outRefDirectoryPath = args[i].substring(3) ;
                }
                else if( args[i].startsWith( "-enum=" ) ) { 
                	this.outEnumDirectoryPath = args[i].substring(6) ;
                }
                else if( args[i].startsWith( "-e=" ) ) { 
                	this.outEnumDirectoryPath = args[i].substring(3) ;
                } 
                else if( args[i].startsWith( "-name=" ) ) { 
                	this.mainFileName = args[i].substring(6) ;
                }
                else if( args[i].startsWith( "-n=" ) ) { 
                	this.mainFileName = args[i].substring(3) ;
                }
            }
            if( this.inDirectoryPath != null 
            	&& 
            	this.configFilePath != null 
            	&&
            	this.outRefDirectoryPath != null
            	&&
            	this.outEnumDirectoryPath != null
            	&&
            	this.mainFileName != null
            	) {
                retVal = true ;
            }
        }       
        return retVal ;
    }
    
    public void printArgs( String[] args ) {
    	System.out.println( "\nFollowing args were received: " ) ;
    	for( int i=0; i<args.length; i++ ) {
    		System.out.println( args[i] ) ;
    	}
    }
    
    public class MetadataRefinerException extends Exception {
    	
		private static final long serialVersionUID = 1L;

		public MetadataRefinerException(String message, Throwable cause) {
			super(message, cause);
		}

		public MetadataRefinerException(String message) {
			super(message);
		}
    	
    }

}
