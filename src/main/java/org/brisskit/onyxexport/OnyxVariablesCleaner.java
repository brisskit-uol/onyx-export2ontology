/**
 * 
 */
package org.brisskit.onyxexport;

import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import org.brisskit.onyxdata.beans.ValueSetDocument;
import org.brisskit.onyxdata.beans.VariableValueType;
import org.brisskit.onyxvariables.beans.VariableType;
import org.brisskit.onyxvariables.beans.VariablesDocument;


/**
 * @author jl99
 *
 */
public class OnyxVariablesCleaner {

	private static final String USAGE =
        "Usage: OnyxVariablesCleaner {Parameters}\n" +       
        "Parameters:\n" +
        " -export=path-to-onyx-export-directory\n" +
        "Notes:\n" +
        " (1) Parameter triggers can be shortened to the first letter; ie: -i,-o.\n" +
        " (2) The export parameter is mandatory.\n" +
        " (3) The export directory must exist and contain an onyx export with files" +
        "     enhanced with appropriate XML name spaces." ;
	
	private static Log log = LogFactory.getLog( OnyxVariablesCleaner.class ) ;
	
    private static String inDirectoryPath = null ;
    
    private File directory ;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//
		// Retrieve command line arguments...
		boolean good = retrieveArgs( args ) ;		
		if( !good ) {
			System.out.println( USAGE ) ; 
			System.exit(1) ;
		}
		//
		// Vet input directory for existence...
		File inputDirectory = new File( inDirectoryPath ) ;
		if( !inputDirectory.exists() ) {
			System.out.println( "Input directory does not exist: [" + inDirectoryPath + "]" ) ; 
			System.exit(1) ;
		}		
		String[] fileNames = inputDirectory.list() ;
		//
		// Bail out if no files exist...
		if( fileNames.length == 0 ) {
			System.out.println( "Input directory is empty." ) ;
			System.exit(1) ;
		}
		//
		// Process the input directory...
		for( int i=0; i<fileNames.length; i++ ) {
			
			File candidateQuestionnaireDirectory = new File( inputDirectory.getAbsolutePath(), fileNames[i] ) ;
			//
			// Bypass hidden directories or those beginning with a dot.
			// (Useful in development when .svn directories can get in the way)... 
			if( candidateQuestionnaireDirectory.isHidden() || candidateQuestionnaireDirectory.getName().startsWith( ".") ) {
				continue ;
			}
			//
			// Everything at the top level to be processed is a directory...
			if( candidateQuestionnaireDirectory.isDirectory() ) {
				
				try {
					log.debug( "Processing: " + candidateQuestionnaireDirectory.getName() ) ;
					if( candidateQuestionnaireDirectory.getName().equals( "RiskFactorQuestionnaire" ) ) {
						System.out.println( "OnyxVariablesCleaner cleaning: RiskFactorQuestionnaire" ) ;
						OnyxVariablesCleaner ovc = new OnyxVariablesCleaner( candidateQuestionnaireDirectory ) ;
						ovc.cleanRiskFactorQuestionnaire() ;
					}
				}
				//
				// Catch all exceptions...
				catch( Exception ex ) {
					ex.printStackTrace() ; 
					System.exit(1) ;
				}
			}
		}
		//
		// We appear to have been successful...
		System.out.println( "OnyxVariablesCleaner: Done!" ) ;	
		System.exit(0) ;
	}

	private OnyxVariablesCleaner( File directory ) throws XmlException, IOException {
		this.directory = directory ;

	}
	
	private void cleanRiskFactorQuestionnaire() throws Exception {
		File[] files = directory.listFiles() ;
		for( File file : files ) {
			String fileName = file.getName();
			//
			// We need to process the variables file...
			if( fileName.equals( "variables.xml" ) ) {
				cleanRFQVariablesFile( file ) ;
			}
			//
			// We ignore the enitities file...
			else if( fileName.equals( "entities.xml" ) ) {
				log.debug( "Skipping " + fileName ) ;
				continue ;
			}
			else {
				//
				// The format of the name of a participant data file is nnnnnnn.xml
				// Check on this format before selecting for processing...
				String basename = fileName.split( "\\." )[0] ;
				try {
					Integer.valueOf( basename ) ;
					cleanRFQDataFiles( file ) ;
				}
				catch( NumberFormatException nfx ) {
					log.debug( "Skipping " + fileName ) ;
					continue ;
				}
			}
		}
		
	}
	
	private void cleanRFQVariablesFile( File variablesFile ) throws Exception {			
		VariablesDocument varDoc = VariablesDocument.Factory.parse( variablesFile ) ;
		VariableType[] vtArray = varDoc.getVariables().getVariableArray() ;
		for( VariableType vt : vtArray ) {
			//
			// Correct the name for father_die_age...
			if( vt.getName().equals( "father_die_age_cat.AGE_FAM.AGE_FAM") ) {
				vt.setName( "father_die_age" ) ;
			}
		}
		//
		// Save the file in-place (overwrites the old file)...
		save( variablesFile, varDoc ) ;
	}
	
	private void cleanRFQDataFiles( File valueSetFile ) throws Exception {
		ValueSetDocument vsDoc = ValueSetDocument.Factory.parse( valueSetFile ) ;
		VariableValueType[] vvtArray = vsDoc.getValueSet().getVariableValueArray() ;
		for( VariableValueType vvt : vvtArray ) {
			//
			// Correct the name for father_die_age...
			if( vvt.getVariable().equals( "father_die_age_cat.AGE_FAM.AGE_FAM") ) {
				vvt.setVariable( "father_die_age" ) ;
			}
		}
		//
		// Save the file in-place (overwrites the old file)...
		save( valueSetFile, vsDoc ) ;
	}
	
    private static boolean retrieveArgs( String[] args ) {
        boolean retVal = false ;
        if( args != null && args.length > 0 ) {
            
            for( int i=0; i<args.length; i++ ) {
                
            	if( args[i].startsWith( "-export=" ) ) { 
            		OnyxVariablesCleaner.inDirectoryPath = args[i].substring(8) ;
                }
                else if( args[i].startsWith( "-e=" ) ) { 
                	OnyxVariablesCleaner.inDirectoryPath = args[i].substring(3) ;
                }                 
                
            }
            if( OnyxVariablesCleaner.inDirectoryPath != null ) {
                retVal = true ;
            }
        }       
        return retVal ;
    }
    
    public void save( File file, XmlObject document ) throws IOException {
		XmlOptions opts = getSaveOptions() ;
		document.save( file, opts ) ;
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

}
