/**
 * 
 */
package org.brisskit.onyxexport;

import java.io.File ;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.apache.commons.logging.Log ;
import org.apache.commons.logging.LogFactory ;

import org.brisskit.onyxvariables.beans.*;
import org.brisskit.onyxvariables.beans.VariableType;
import org.brisskit.onyxmetadata.stageone.beans.*;
import org.apache.xmlbeans.* ;

/**
 * @author jl99
 *
 */
public class OnyxVariables2Metadata {
	
	private static final String USAGE =
        "Usage: OnyxVariables2Metadata {Parameters}\n" +       
        "Parameters:\n" +
        " -input=path-to-onyx-export-directory\n" +
        " -output=path-to-working-directory\n" +
        "Notes:\n" +
        " (1) Parameter triggers can be shortened to the first letter; ie: -i,-o.\n" +
        " (2) The input parameter is the only mandatory one.\n" +
        " (3) If the output path parameter is omitted, output is directed to standard out.\n" +
        " (4) If the output path parameter is provided, the working directory must not exist." ;
	
	private static Log log = LogFactory.getLog( OnyxVariables2Metadata.class ) ;
		
	private static final String SOURCE = "onyx" ;
	
    private static String inDirectoryPath = null ;
    private static String outDirectoryPath = null ;

	private VariablesDocument varDoc ;
	private Source source = null  ;
	
	private static StringBuffer logIndent = null ;

	/**
	 * @param args
	 */
	public static void main( String[] args ) {
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
		// If provided, vet output directory for non existence...
		File outputDirectory = null ;
		if( outDirectoryPath != null ) {
			outputDirectory = new File( outDirectoryPath ) ;
			if( outputDirectory.exists() ) {
				System.out.println( "Output directory already exists: [" + outDirectoryPath + "]" ) ;
				System.exit(1) ;
			}
			outputDirectory.mkdirs() ;
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
				//
				// For any given questionnaire directory, retrieve the variables file...
				File variablesFile = new File(  candidateQuestionnaireDirectory.getAbsolutePath(), "variables.xml" ) ;			
				System.out.println( "OnyxVariables2Metadata processing: " + variablesFile.getAbsolutePath() ) ;
				try {
					OnyxVariables2Metadata v2m = OnyxVariables2Metadata.Factory.newInstance( variablesFile ) ;
					v2m.exec() ;
					//
					// Print to standard out if no output directory was supplied...
					if( outputDirectory == null ) {
						v2m.print() ;
					}
					else {					
						v2m.save( outputDirectory + File.separator + fileNames[i] + ".xml" );
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
		System.out.println( "OnyxVariables2Metadata: Done!" ) ;	
		System.exit(0) ;
	}

	private OnyxVariables2Metadata() {}
	
	public void exec() {
		if( log.isTraceEnabled() ) enterTrace( "exec" ) ;
			this.source.process() ;
		if( log.isTraceEnabled() ) exitTrace( "exec" ) ;
	}	
	
	public void print() {
		XmlOptions opts = getSaveOptions() ;
		System.out.println( source.sourceDocument.xmlText(opts) ) ;
	}
	
	public void save( String fullPath ) throws IOException {
		XmlOptions opts = getSaveOptions() ;
		source.sourceDocument.save( new File( fullPath ), opts ) ;
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
	 * @see        org.astrogrid.AdqlParser#exitTrace(String)
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
     * @see        org.astrogrid.AdqlParser#enterTrace(String)
     */
    public static void exitTrace( String entry ) {
    	indentMinus() ;
		log.trace( getIndent().toString() + "exit : " + entry ) ;
	}
	
    /**
     * Utility method used to maintain the structured trace log.
     * @see        org.astrogrid.AdqlParser#indentMinus()
     */
    public static void indentPlus() {
		getIndent().append( ' ' ) ;
	}
	
    /**
     * Utility method used to maintain the structured trace log.
     * @see        org.astrogrid.AdqlParser#indentPlus()
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
    
    private static void resetIndent() {
        if( logIndent != null ) { 
            if( logIndent.length() > 0 ) {
               logIndent.delete( 0, logIndent.length() )  ;
            }
        }   
    }

	public static final class Factory {

		public static OnyxVariables2Metadata newInstance( java.io.File file ) throws org.apache.xmlbeans.XmlException, java.io.IOException {
			if( log.isTraceEnabled() ) enterTrace( "Factory.newInstance" ) ;
			OnyxVariables2Metadata v2m = new OnyxVariables2Metadata() ;
			v2m.varDoc = VariablesDocument.Factory.parse( file ) ;
			log.debug( "File parsed successfully." ) ;
			divideVariablesIntoCollections( v2m ) ;	
			if( log.isTraceEnabled() ) exitTrace( "Factory.newInstance" ) ;
			return v2m ; 
		}
		
		private static void divideVariablesIntoCollections( OnyxVariables2Metadata v2m ) {	
			if( log.isTraceEnabled() ) enterTrace( "Factory.divideVariablesIntoCollections" ) ;
			if( isStage( v2m) ) {
				newStage( v2m ) ;
			}
			else {
				newEntity( v2m ) ;
			}
			if( log.isTraceEnabled() ) exitTrace( "Factory.divideVariablesIntoCollections" ) ;
		}
		
		/**
		 *  
		 * @return true if this is a stage.
		 */
		private static boolean isStage( OnyxVariables2Metadata v2m) {
			return ( getStageName( v2m ) == null ? false : true ) ;
		}
		
		/**
		 * 
		 * If any variable within the file has an attribute of "stage"
		 * Then its stage value is assumed to be the stage name.
		 *  
		 * @return Stage name or null
		 */
		private static String getStageName( OnyxVariables2Metadata v2m ) {
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			for( int i=0; i<vta.length; i++ ) {
				if( vta[i].isSetAttributes() ) {
					AttributeType[] ata = vta[i].getAttributes().getAttributeArray() ;
					for( int j=0; j<ata.length; j++ ) {
						if( ata[j].getName().equalsIgnoreCase( "stage" ) ) {
							return ata[j].getStringValue() ;
						}
					}
				}
			}
			return null ;
		}
		
		private static void newStage( OnyxVariables2Metadata v2m ) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.newStage" ) ;
			if( isQuestionnaire( v2m) ) {
				newQuestionnaire( v2m ) ;
			}
			else {
				newNonQuestionnaire( v2m ) ;
			}
			if( log.isTraceEnabled() ) exitTrace( "Factory.newStage" ) ;
		}
		
		private static void newNonQuestionnaire( OnyxVariables2Metadata v2m ) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.newNonQuestionnaire" ) ;
			v2m.source = v2m.new NonQuestionnaireStage( SOURCE ) ;

			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			for( int i=0; i<vta.length; i++ ) {
				v2m.source.nqv.variables.add( vta[i] ) ;
			}	
			if( log.isTraceEnabled() ) exitTrace( "Factory.newNonQuestionnaire" ) ;
		}
		
		private static void newQuestionnaire( OnyxVariables2Metadata v2m ) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.newQuestionnaire" ) ;
			v2m.source = v2m.new QuestionnaireStage( SOURCE ) ;
			gatherNonQuestionVariables( v2m ) ;
			gatherQuestionVariables( v2m ) ;
			if( log.isTraceEnabled() ) exitTrace( "Factory.newQuestionnaire" ) ;
		}
		
		private static void gatherNonQuestionVariables( OnyxVariables2Metadata v2m ) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.gatherNonQuestionVariables" ) ;
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			for( int i=0; i<vta.length; i++ ) {
				if( !isQuestion( vta[i] ) ) {
					v2m.source.nqv.variables.add( vta[i] ) ;
				}			
			}
			if( log.isTraceEnabled() ) exitTrace( "Factory.gatherNonQuestionVariables" ) ;
		}
		
		private static boolean isQuestion( VariableType vt ) {
			AttributeType[] ata = vt.getAttributes().getAttributeArray() ;
			for( int i=0; i<ata.length; i++ ) {
				if( ata[i].getName().equalsIgnoreCase( "questionName" ) ) {
					return true ;
				}
			}
			return false ;
		}
		
		private static void gatherQuestionVariables( OnyxVariables2Metadata v2m ) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.gatherQuestionVariables" ) ;
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			for( int i=0; i<vta.length; i++ ) {
				if( isQuestion( vta[i] ) ) {
					if( isParentQuestion( vta[i] ) ) {
						i = gatherParentQuestionVariables( v2m, i ) ;
					}
					else if( isDataSubmissionQuestionnaire_Kludge( v2m, i ) ) {
						i = kludge_gatherObservationsDuringCurrentPeriodOfCare( v2m, i ) ;
					}
					else {
						i = gatherSingletonQuestionVariables( v2m, i ) ;
					}
				}			
			}
			if( log.isTraceEnabled() ) exitTrace( "Factory.gatherQuestionVariables" ) ;
		}
		
		private static boolean isParentQuestion(  VariableType vt  ) {
			boolean retValue = false ;
			try {
				if( vt.isSetAttributes() ) {
					AttributeType[] ata = vt.getAttributes().getAttributeArray() ;
					for( int i=0; i<ata.length; i++ ) {
						if( ata[i].getName().equalsIgnoreCase( "parentQuestion" ) ) {
							if( ata[i].getStringValue().equalsIgnoreCase( "true" ) ) {
								retValue = true ;
							}
							break ;
						}
					}
				}
			}
			finally {
				;
			}
			return retValue ;
		}
		
		private static int gatherParentQuestionVariables( OnyxVariables2Metadata v2m, int driverDisplacement ) {
			if( isDataSubmissionQuestionnaire_Kludge( v2m, driverDisplacement ) )
				return driverDisplacement ;
			QuestionnaireStage qs = (QuestionnaireStage)v2m.source ;
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			VariableType driver = vta[ driverDisplacement ] ;
			String driverQuestionName = getQuestionName( driver ) ;
			int i=driverDisplacement+1 ;
			ParentQuestion pq = v2m.new ParentQuestion() ;
			VariableType firstChild = null ;
			pq.variables.add( driver ) ;
			for( ; i<vta.length; i++ ) {
				if( driverQuestionName.equals( getQuestionName( vta[ i] ) ) ) {
					pq.variables.add( vta[i] ) ;
					continue ;
				}
				else if( !isParentQuestion( vta[i]) 
						 && 
						 !isSectionChanged( vta, i ) 
						) {	
					if( firstChild == null ) {
						firstChild = vta[i] ;
						i = gatherChildQuestionVariables( v2m, i, pq ) ;
						continue ;
					}
					else if( areAssociated( vta[i], firstChild ) ) {
						i = gatherChildQuestionVariables( v2m, i, pq ) ;
						continue ;
					}					
				}
				break ;
			}
			qs.questions.list.add( pq ) ;
			return --i;
		}
		
		private static boolean isDataSubmissionQuestionnaire_Kludge( OnyxVariables2Metadata v2m, int i ) {
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			if( vta[i].getName().equals( "epi_obshr_cat" ) )
				return true ;
			return false ;
		}
		
		private static int kludge_gatherObservationsDuringCurrentPeriodOfCare( OnyxVariables2Metadata v2m, int driverDisplacement ) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.kludge_gatherObservationsDuringCurrentPeriodOfCare" ) ;
			QuestionnaireStage qs = (QuestionnaireStage)v2m.source ;
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			int i=driverDisplacement;
			Kludge_ObservationsDuringCurrentPeriodOfCareQuestion 
				pq = v2m.new Kludge_ObservationsDuringCurrentPeriodOfCareQuestion() ;
			VariableType firstChild = null ;
			//
			// NB: This does NOT have a parent question -
			//     that is the minor questionnaire fault we are trying to overcome.
			// So for the moment, we need just to gather "child" questions...
			for( ; i<vta.length; i++ ) {
				if( !isSectionChanged( vta, i ) ) {	
					if( firstChild == null ) {
						firstChild = vta[i] ;
						i = gatherChildQuestionVariables( v2m, i, pq ) ;
						continue ;
					}
					else if( areAssociated( vta[i], firstChild ) ) {
						i = gatherChildQuestionVariables( v2m, i, pq ) ;
						continue ;
					}					
				}
				break ;
			}
			qs.questions.list.add( pq ) ;
			if( log.isTraceEnabled() ) exitTrace( "Factory.kludge_gatherObservationsDuringCurrentPeriodOfCare" ) ;
			return --i;
		}
		
		private static boolean kludge_isObservationDuringCurrentPeriodOfCare( VariableType target ) {
			if( target.getName().equals( "epi_obshr_cat" ) )
				return true ;
			return false ;
		}
		
		private static boolean areAssociated( VariableType target, VariableType firstChild ) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.areAssociated" ) ;
			boolean associated = false ;			
			try {

				//
				// This is a kludge for the DataSubmissionQuestionnaire
				// which has a minor structural fault regarding
				// "Observations during current episode of care."
				if( kludge_isObservationDuringCurrentPeriodOfCare( target ) )
					return false ;

				String[] firstChildParts = firstChild.getName().split( "\\." ) ;
				String[] targetParts = target.getName().split( "\\." ) ;
				if( targetParts[0].equals( firstChild.getName() ) ) {
					associated = true ;
				}
				else if( targetParts[0].equals( firstChildParts[0] ) ) {
					associated = true ;
				}
				else {
					String[] fcp1 =  firstChildParts[0].split( "_" ) ;
					String[] tp1 = targetParts[0].split( "_" ) ;
					if( tp1.length >= fcp1.length ) {
						int smallest = ( tp1.length <= fcp1.length ? tp1.length : fcp1.length ) ;
						associated = true ;
						for( int i=0 ; i<smallest-1; i++ ) {
							if( !fcp1[i].equals(tp1[i]) ) {
								associated = false ;
								break ;
							}
						}	
						if( !associated ) {
							associated = true ;
							for( int i=smallest-1; i>0; i-- ) {
								if( !fcp1[i].equals(tp1[i]) ) {
									associated = false ;
									break ;
								}
							}
						}	
						if( !associated 
								&& 
								fcp1.length == tp1.length 
								&& 
								tp1.length >= 3
								&&
								tp1[0].equals(fcp1[0])
								&&
								tp1[tp1.length-1].equals( fcp1[fcp1.length-1] ) ) {

							int diffCount = 0 ;
							for( int i=0; i<tp1.length; i++ ) {
								if( !tp1[i].equals( fcp1[i] ) ) {
									diffCount++ ;
								}
							}
							if( diffCount <= 1 ) {
								associated = true ;
							}
						}
					}				
				}
				if( log.isDebugEnabled() ) {
					log.debug( "\nfirstChild name: " + firstChild.getName() + "\n" +
							"target name:     " + target.getName() + "\n" +
							"associated: " + associated ) ;
				}
				return associated ;
			}
			finally {
				if( log.isTraceEnabled() ) exitTrace( "Factory.areAssociated" ) ;
			}
		}
		
		private static boolean isSectionChanged( VariableType[] vta, int index ) {
			AttributeType[] previousAttributeArray = vta[index-1].getAttributes().getAttributeArray() ;
			String sectionNameOne = null ;
			for( int i=0; i<previousAttributeArray.length; i++ ) {
				if( previousAttributeArray[i].getName().equalsIgnoreCase( "section" ) ) {
					sectionNameOne =  previousAttributeArray[i].getStringValue() ;
					break ;
				}
			}
			AttributeType[] currentAttributeArray = vta[index].getAttributes().getAttributeArray() ;
			String sectionNameTwo = null ;
			for( int i=0; i<currentAttributeArray.length; i++ ) {
				if( currentAttributeArray[i].getName().equalsIgnoreCase( "section" ) ) {
					sectionNameTwo =  currentAttributeArray[i].getStringValue() ;
					break ;
				}
			}
			if( sectionNameOne.equalsIgnoreCase( sectionNameTwo) ) {
				return false ;
			}
			return true ;
		}
		
		private static int gatherChildQuestionVariables( OnyxVariables2Metadata v2m, int driverDisplacement, ParentQuestion pq ) {
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			VariableType driver = vta[ driverDisplacement ] ;
			String driverQuestionName = getQuestionName( driver ) ;
			int i=driverDisplacement+1 ;
			ChildQuestion cq = v2m.new ChildQuestion() ;
			cq.variables.add( driver ) ;
			for( ; i<vta.length; i++ ) {
				if( driverQuestionName.equals( getQuestionName( vta[ i] ) ) ) {
					cq.variables.add( vta[i] ) ;
					continue ;
				}
				break ;
			}
			pq.children.add( cq ) ;
			return --i;
		}	
		
		private static int gatherSingletonQuestionVariables( OnyxVariables2Metadata v2m, int driverDisplacement ) {
			QuestionnaireStage qs = (QuestionnaireStage)v2m.source ;
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray() ;
			VariableType driver = vta[ driverDisplacement ] ;
			String driverQuestionName = getQuestionName( driver ) ;
			if( driverQuestionName.equalsIgnoreCase( "epi_symptomother_cat" ) ) {
				log.debug( "epi_symptomother_cat" ) ;
			}
			int i=driverDisplacement+1 ;
			SingletonQuestion sq = v2m.new SingletonQuestion() ;
			sq.variables.add( driver ) ;
			for( ; i<vta.length; i++ ) {
				if( driverQuestionName.equals( getQuestionName( vta[ i] ) ) ) {
					sq.variables.add( vta[i] ) ;
					continue ;
				}
				break ;
			}
			qs.questions.list.add( sq ) ;
			return --i;
		}	
		
		private static boolean isQuestionnaire( OnyxVariables2Metadata v2m ) {
			return ( getQuestionnaireName( v2m ) == null ? false : true ) ;
		}
		
		private static String getQuestionnaireName( OnyxVariables2Metadata v2m ) {
			//
			// Retrieve the attributes of the first variable present in the file...
			AttributeType[] ata = v2m.varDoc.getVariables().getVariableArray()[0].getAttributes().getAttributeArray() ;
			for( int i=0; i<ata.length; i++ ) {
				if( ata[i].getName().equalsIgnoreCase( "questionnaire" ) ) {
					return ata[i].getStringValue() ;
				}
			}
			return null ;
		}
		
		private static String getQuestionName( VariableType vt ) {
			AttributeType[] ata = vt.getAttributes().getAttributeArray() ;
			for( int i=0; i<ata.length; i++ ) {
				String attributeName = ata[i].getName() ;
				if( attributeName.equalsIgnoreCase( "questionName" ) ) {
					return ata[i].getStringValue() ;
				}
			}
			return "" ;
		}
		
		private static void newEntity(OnyxVariables2Metadata v2m) {
			if( log.isTraceEnabled() ) enterTrace( "Factory.newEntity" ) ;
			v2m.source = v2m.new Entity(SOURCE);
			VariableType[] vta = v2m.varDoc.getVariables().getVariableArray();
			for (int i = 0; i < vta.length; i++) {
				v2m.source.nqv.variables.add(vta[i]);
			}
			if( log.isTraceEnabled() ) exitTrace( "Factory.newEntity" ) ;
		}

	} // end of class Factory
	
	private abstract class Source {
		
		protected SourceDocument sourceDocument ;
		protected NonQuestionVariables nqv = new NonQuestionVariables() ;
		
		Source( String sourceName ) {
			this.sourceDocument = SourceDocument.Factory.newInstance() ; 	
			this.sourceDocument.addNewSource().setName( sourceName ) ;
		}
		
		public abstract void process() ;
		
		protected ArrayList<org.brisskit.onyxmetadata.stageone.beans.VariableType> processStructuredVariables() {
			if( log.isTraceEnabled() ) enterTrace( "Source.processStructuredVariables" ) ;
			Iterator<VariableType> vit = this.nqv.variables.listIterator() ;
			HashMap<String, org.brisskit.onyxmetadata.stageone.beans.VariableType> structuredVarsMap = new HashMap<String, org.brisskit.onyxmetadata.stageone.beans.VariableType>( 256 ) ;
			while( vit.hasNext() ) {
				processStructuredVariable( vit.next(), structuredVarsMap ) ;
			}
			Iterator<org.brisskit.onyxmetadata.stageone.beans.VariableType> sit = structuredVarsMap.values().iterator() ;
			ArrayList<org.brisskit.onyxmetadata.stageone.beans.VariableType> list = new ArrayList<org.brisskit.onyxmetadata.stageone.beans.VariableType>() ;
			while( sit.hasNext() ) {
				org.brisskit.onyxmetadata.stageone.beans.VariableType oiv = sit.next() ;
				XmlCursor cursor = oiv.newCursor() ;
				try {
					if( !cursor.toParent() ) {
						list.add( oiv ) ;
					}
				}
				finally {
					cursor.dispose() ;
				}
				
			}
			if( log.isTraceEnabled() ) exitTrace( "Source.processStructuredVariables" ) ;
			return list ;
		}
		
		private void processStructuredVariable( org.brisskit.onyxvariables.beans.VariableType ov, 
				                                HashMap<String, org.brisskit.onyxmetadata.stageone.beans.VariableType> svm) {
			if( log.isTraceEnabled() ) enterTrace( "Source.processStructuredVariable" ) ;
			String[] parts = ov.getName().split( "\\." ) ;
			StringBuilder buffer = new StringBuilder( 256 ) ;
			org.brisskit.onyxmetadata.stageone.beans.VariableType oiv = null ;
			org.brisskit.onyxmetadata.stageone.beans.VariableType oivParent = null ;
			for( int i=0; i<parts.length; i++ ) {
				oivParent = svm.get( buffer.toString() ) ;
				if( oivParent != null ) {
					buffer.append( '.' ) ;
				}
				buffer.append( parts[i] ) ;
				String name = buffer.toString() ;
				if( !svm.containsKey( name ) ) {
					if( oivParent != null ) {
						oiv = oivParent.addNewVariable() ;
					}
					else {
						oiv = org.brisskit.onyxmetadata.stageone.beans.VariableType.Factory.newInstance() ;
					}
					oiv.setName( parts[i] ) ;
					svm.put( name, oiv ) ;
				}
			}
			oiv.setType( ov.getValueType() ) ;
			if( ov.isSetRepeatable() ) {
				if( ov.getRepeatable().equalsIgnoreCase( "true" ) ) {
					oiv.setRepeatable( true ) ;
				}
			}
			if( ov.isSetCategories() ) {
				RestrictionType et = oiv.addNewRestriction() ;
				CategoryType[] cta = ov.getCategories().getCategoryArray() ;
				for( int i=0; i<cta.length; i++ ) {
					if( cta[i].isSetAttributes() ) {
						return ;
					}
					et.addNewEnum().setStringValue( cta[i].getName() ) ;
				}
			}
			if( log.isTraceEnabled() ) exitTrace( "Source.processStructuredVariable" ) ;
		} 
		
	} // end of class Source
	
	private abstract class Stage extends Source {
		
		Stage( String sourceName ) {
			super( sourceName ) ;
			StageType st = this.sourceDocument.getSource().addNewStage() ;
			st.setName( OnyxVariables2Metadata.Factory.getStageName( OnyxVariables2Metadata.this ) ) ;
		}
		
		public void process() {
			if( log.isTraceEnabled() ) enterTrace( "Stage.process" ) ;
			ArrayList<org.brisskit.onyxmetadata.stageone.beans.VariableType> list = processStructuredVariables() ;
			StageType et = sourceDocument.getSource().getStage() ;
			org.brisskit.onyxmetadata.stageone.beans.VariableType[] vta = list.toArray( new org.brisskit.onyxmetadata.stageone.beans.VariableType[ list.size() ] ) ;
			et.setVariableArray( vta ) ;
			if( log.isTraceEnabled() ) exitTrace( "Stage.process" ) ;
		}
		
	}
	
	private class NonQuestionnaireStage extends Stage {
		
		NonQuestionnaireStage(String sourceName) {
			super(sourceName);
		}

	}
	
	private class QuestionnaireStage extends Stage {
		
		private Questions questions = new Questions() ;
		private SectionType currentSection = null ;
		
		public QuestionnaireStage(String sourceName) {
			super(sourceName);
		}
		
		public void process() {
			if( log.isTraceEnabled() ) enterTrace( "QuestionnaireStage.process" ) ;

			processNonQuestionVariables() ;

			processQuestionVariables() ;
			
			if( log.isTraceEnabled() ) exitTrace( "QuestionnaireStage.process" ) ;
		}
		
		private void processQuestionVariables() {
			if( log.isTraceEnabled() ) enterTrace( "processQuestionVariables" ) ;
			
			Iterator<Question> it = questions.list.listIterator() ;
			while( it.hasNext() ) {
				Question qu = it.next() ;
				if( isNewSectionRequired( currentSection, qu ) ) {
					currentSection = sourceDocument.getSource().getStage().addNewSection() ;
					currentSection.setName( getSectionName( qu ) ) ;
				}
				qu.buildQuestion( currentSection ) ;				
			}
			if( log.isTraceEnabled() ) exitTrace( "processQuestionVariables" ) ;
		}
		
		private boolean isNewSectionRequired( SectionType section, Question question ) {
			if( log.isTraceEnabled() ) enterTrace( "isNewSectionRequired" ) ;
			try {
				String previousSectionName = ( section == null ? "" : section.getName() ) ;
				String sectionName = getSectionName( question ) ;
				if( sectionName.equalsIgnoreCase( previousSectionName ) )
					return false ;
				if( log.isDebugEnabled() ) {
					log.debug( "new section: " + sectionName ) ;
				}
				return true ;
			}
			finally {
				if( log.isTraceEnabled() ) exitTrace( "isNewSectionRequired" ) ;
			}
		}
		
		private String getSectionName( Question question ) {
			//
			// Guard added after kludge for DataSubmissionQuestionnaire coded...
			if( question instanceof Kludge_ObservationsDuringCurrentPeriodOfCareQuestion )
				return "MAIN" ;
			
			AttributeType[] ata = question.variables.get(0).getAttributes().getAttributeArray() ;
			for( int i=0; i<ata.length; i++ ) {
				if( ata[i].getName().equalsIgnoreCase( "section" ) ) {
					return ata[i].getStringValue() ;
				}
			}
			return null ;
		}

		
		private void processNonQuestionVariables() {
			if( log.isTraceEnabled() ) enterTrace( "QuestionnaireStage.processNonQuestionVariables" ) ;
			LinkedHashSet<String> qualifiers = gatherHighLevelQualifiers() ; 
			log.debug( "qualifiers number: " + qualifiers.size() ) ;
			StageType stage = sourceDocument.getSource().getStage() ;
			Iterator<String> qit = qualifiers.iterator() ;
			while( qit.hasNext() ) {
				String qualifier = qit.next() ;
				org.brisskit.onyxmetadata.stageone.beans.VariableType containingVar = stage.addNewVariable() ;
				containingVar.setName( qualifier ) ;
				Iterator<VariableType> vit = this.nqv.variables.listIterator() ;
				while( vit.hasNext() ) {
					VariableType v = vit.next() ;
					if( v.getName().startsWith( qualifier + '.' ) ) {
						String name = v.getName().split( "\\." )[1] ;
						org.brisskit.onyxmetadata.stageone.beans.VariableType containedVar = containingVar.addNewVariable() ;
						containedVar.setName( name ) ;
						containedVar.setType( v.getValueType() ) ;
						if( v.isSetRepeatable() ) {
							if( v.getRepeatable().equalsIgnoreCase( "true" )  ) {
								containedVar.setRepeatable( true ) ;
							}
						}
					}
				}	
			}
			if( log.isTraceEnabled() ) exitTrace( "QuestionnaireStage.processNonQuestionVariables" ) ;

		}
		
		private LinkedHashSet<String> gatherHighLevelQualifiers() {
			LinkedHashSet<String> qualifiers = new LinkedHashSet<String>() ; 
			Iterator<VariableType> it = this.nqv.variables.listIterator() ;
			while( it.hasNext() ) {
				VariableType v = it.next() ;
				if( v.getName().contains( "." ) ) {
					String qualifier = v.getName().split( "\\." )[0] ;
					qualifiers.add( qualifier ) ;
				}
			}
			return qualifiers ;
		}
		
	}
	
	private class Entity extends Source {		
		
		Entity( String sourceName ) {
			super( sourceName ) ;
			EntityType et = this.sourceDocument.getSource().addNewEntity() ;
			et.setName( this.getEntityName() ) ;
		}
		
		public void process() {
			if( log.isTraceEnabled() ) enterTrace( "Entity.process" ) ;
			ArrayList<org.brisskit.onyxmetadata.stageone.beans.VariableType> list = processStructuredVariables() ;
			EntityType et = sourceDocument.getSource().getEntity() ;
			org.brisskit.onyxmetadata.stageone.beans.VariableType[] 
			    vta = list.toArray( new org.brisskit.onyxmetadata.stageone.beans.VariableType[ list.size() ] ) ;
			//
			// The concept vital_status is present in the i2b2 demo systems and is one variable within the
			// demo systems on which the i2b2 query tool is set up to do breakdown analyses.
			// Vital_status as such is not present within the Onyx metadata or data, simply because as a
			// questionnaire tool the participant is assumed to be alive when interviewed.  :-)
			// 
			// Vital_status is added here to fill this gap.
			// You need also to consider programmes MetadataRefiner and OnyxData2Pdo:
			// The former adds enumerations to the concept. The latter ensures suitable values are added
			// to the Patient Data Object (ie: patient dimension and observation fact).
			// 
			if( et.getName().equalsIgnoreCase( "Participant" ) ) {
				 outerLoop:	for( int i=0; i<vta.length; i++ ) {
					org.brisskit.onyxmetadata.stageone.beans.VariableType[]
					     vta2 = vta[i].getVariableArray() ;
					for( int j=0; j<vta2.length; j++ ) {
						if( vta2[j].getName().equalsIgnoreCase( "Participant" ) ) {
							org.brisskit.onyxmetadata.stageone.beans.VariableType
								vitalStatus = vta2[j].addNewVariable() ;
							vitalStatus.setName( "vital_status" ) ;
							vitalStatus.setType( "text" ) ;
							break outerLoop ;
						}
					} // inner loop
					
				} // outer loop
			}
			et.setVariableArray( vta ) ;
			if( log.isTraceEnabled() ) exitTrace( "Entity.process" ) ;
		}
		
		private String getEntityName() {
			VariableType[] vta = OnyxVariables2Metadata.this.varDoc.getVariables().getVariableArray() ;
			for( int i=0; i<vta.length; i++ ) {
				if( vta[i].isSetEntityType() ) {
					return vta[i].getEntityType() ;
				}
			}
			return null ;
		}
				
	} // end of class Entity
	
	private class NonQuestionVariables {
		protected ArrayList<VariableType> variables = new ArrayList<VariableType>() ;		
	}
	
	private class Questions {
		private ArrayList<Question> list = new ArrayList<Question>() ;
	}
	
	private abstract class Question {
		protected ArrayList<VariableType> variables = new ArrayList<VariableType>() ;	
		QuestionType question ;
		
		public void buildQuestion( SectionType st ) {
			if( log.isTraceEnabled() ) enterTrace( "Question.buildQuestion" ) ;
			question = st.addNewQuestion() ;
			question.setName( getQuestionName() ) ;
			question.setLabel( getQuestionLabel() ) ;	
			if( log.isTraceEnabled() ) exitTrace( "Question.buildQuestion" ) ;
		}
		
		public String getQuestionName() {
			AttributeType[] ata = variables.get(0).getAttributes().getAttributeArray() ;
			for( int i=0; i<ata.length; i++ ) {
				String attributeName = ata[i].getName() ;
				if( attributeName.equalsIgnoreCase( "questionName" ) ) {
					return ata[i].getStringValue() ;
				}
			}
			return "" ;
		}
		
		protected String getQuestionLabel() {
			AttributeType[] ata = variables.get(0).getAttributes().getAttributeArray() ;
			for( int i=0; i<ata.length; i++ ) {
				String attributeName = ata[i].getName() ;
				if( attributeName.equalsIgnoreCase( "label" ) ) {
					return ata[i].getStringValue() ;
				}
			}
			return "" ;
		}
		
		protected void buildVariablesForQuestion() {
			if( log.isTraceEnabled() ) enterTrace( "Question.buildVariablesForQuestion" ) ;
			org.brisskit.onyxmetadata.stageone.beans.VariableType oiVariable = question.addNewVariable() ;
			oiVariable.setName( getUnqualifiedName( this.variables.get(0).getName() ) );
			oiVariable.setType( this.variables.get(0).getValueType() ) ;
			if( this.variables.get(0).isSetCategories() ) {
				processCategories( oiVariable ) ;
			}
			else {
				processForwardConnectedVariables( oiVariable ) ;
			}
			if( log.isTraceEnabled() ) exitTrace( "Question.buildVariablesForQuestion" ) ;
		}
		
		protected String getUnqualifiedName( String name ) {
			String[] parts = name.split( "\\." ) ;
			StringBuilder b = new StringBuilder( name.length() ) ;
			if( parts.length > 1 ) {
				for( int i=1; i<parts.length; i++ ) {
					b.append( parts[i]).append( '.' ) ;
				}
				b.deleteCharAt( b.length()-1 ) ;
				return b.toString() ;
			}
			return name ;
		}
		
		private void processCategories( org.brisskit.onyxmetadata.stageone.beans.VariableType oiVariable ) {
			if( log.isTraceEnabled() ) enterTrace( "Question.processCategories" ) ;
			VariableType vt = this.variables.get(0) ;
			CategoryType[] cta = vt.getCategories().getCategoryArray() ;
			for( int i=0; i<cta.length; i++ ) {
				org.brisskit.onyxmetadata.stageone.beans.VariableType catVar = oiVariable.addNewVariable() ;
				catVar.setName( cta[i].getName() ) ;
				processForwardCategoryVariables( catVar ) ;		
			}
			processForwardConnectedVariables( oiVariable ) ;
			if( log.isTraceEnabled() ) exitTrace( "Question.processCategories" ) ;
		}
		
		private void processForwardCategoryVariables( org.brisskit.onyxmetadata.stageone.beans.VariableType catVar ) {
			if( log.isTraceEnabled() ) enterTrace( "Question.processForwardCategoryVariables" ) ;
			Iterator<VariableType> it = this.variables.listIterator() ;
			it.next() ; // skip passed the main variable
			while( it.hasNext() ) {
				VariableType v = it.next() ;
				if( v.getName().endsWith( '.' + catVar.getName() ) 
					||
					v.getName().contains(  '.' + catVar.getName() + '.' )				
				) {
					catVar.setType( v.getValueType() ) ;
					AttributeType[] ata = v.getAttributes().getAttributeArray();
					for( int j=0; j<ata.length; j++ ) {
						if( ata[j].getName().equalsIgnoreCase( "label" ) ) {
							catVar.setLabel( ata[j].getStringValue() ) ;
						}
					}				
				}	
			}
			if( log.isTraceEnabled() ) exitTrace( "Question.processForwardCategoryVariables" ) ;
		}
		
		private void processForwardConnectedVariables( org.brisskit.onyxmetadata.stageone.beans.VariableType mainVar ) {
			if( log.isTraceEnabled() ) enterTrace( "Question.processForwardConnectedVariables" ) ;
			Iterator<VariableType> it = this.variables.listIterator() ;
			it.next() ; // skip passed the main variable
			while( it.hasNext() ) {
				VariableType v = it.next() ;
				if( !isCategory( v ) ) {
					if( v.isSetCategories() ) {
						log.debug( "======>> " + v.getName() + " <<======") ;
					}
					//
					// We may need to deal with dot qualified names here
					// eg: tobacco_any.comment
					org.brisskit.onyxmetadata.stageone.beans.VariableType newVar = mainVar.addNewVariable() ;
					String name = rationalizeName( this.variables.get(0).getName(), v.getName() ) ;
					newVar.setName( name ) ;
					newVar.setType( v.getValueType() ) ;
					if( isLabel( v ) ) {
						newVar.setLabel( getLabel( v ) ) ;
					}					
				}
			}
			if( log.isTraceEnabled() ) exitTrace( "Question.processForwardConnectedVariables" ) ;
		}
		
		private boolean isCategory( VariableType vt ) {
			if( log.isTraceEnabled() ) enterTrace( "Question.isCategory" ) ;
			try {
				//
				// If the name is not dot qualified,
				// Then in my terms this cannot be a category variable,
				// So we return false immediately.
				// NB: Some that are nevertheless classed as categories are not dot qualified,
				//     but are also not explicitly defined earlier with a category element.
				//     For example, an integer measure of the amount of alcohol.
				//     This condition rules those out, but they get processed later as connected variables.
				if( !vt.getName().contains( "." ) ) {
					return false ;
				}

				if( vt.isSetAttributes() ) {
					AttributeType[] ata = vt.getAttributes().getAttributeArray() ;
					for( int i=0; i<ata.length; i++ ) {
						if( ata[i].getName().equalsIgnoreCase( "categoryName" ) ) {
							return true ;
						}
					}
				}
				return false ;
			}
			finally {
				if( log.isTraceEnabled() ) exitTrace( "Question.isCategory" ) ;
			}
		}
		
		private boolean isLabel( VariableType vt ) {
			return ( getLabel(vt) == null ? false : true ) ;
		}
		
		private String getLabel( VariableType vt ) {
			AttributeType[] ata = vt.getAttributes().getAttributeArray() ;
			for( int i=0; i<ata.length; i++ ) {
				if( ata[i].getName().equalsIgnoreCase( "label" ) ) {
					return ata[i].getStringValue() ;
				}
			}
			return null ;
		}
		
		private String rationalizeName( String mainVar, String minorVar ) {
			if( log.isTraceEnabled() ) enterTrace( "Question.rationalizeName" ) ;
			try { 
				String[] partsMinor = minorVar.split( "\\." ) ;
				if( partsMinor.length == 1 ) {
					return minorVar ;
				}
				else if( partsMinor[0].equals( mainVar ) ) {
					StringBuilder builder = new StringBuilder( minorVar.length() ) ;
					for( int i=1; i<partsMinor.length; i++ ) {
						builder.append( partsMinor[i] ).append( '.' ) ;
					}
					builder.deleteCharAt( builder.length()-1 ) ;
					return builder.toString() ;
				}
				return minorVar ;
			}
			finally {
				if( log.isTraceEnabled() ) exitTrace( "Question.rationalizeName" ) ;
			}
		}

	} // end of class Question
	

	private class SingletonQuestion extends Question {
		
		public void buildQuestion( SectionType st ) {
			super.buildQuestion( st ) ;
			if( log.isTraceEnabled() ) enterTrace( "SingletonQuestion.buildQuestion" ) ;
			buildVariablesForQuestion() ;
			if( log.isTraceEnabled() ) exitTrace( "SingletonQuestion.buildQuestion" ) ;
		}
	}
	
	private class ChildQuestion extends Question {
		
		public void buildQuestion( QuestionType qt ) {
			if( log.isTraceEnabled() ) enterTrace( "ChildQuestion.buildQuestion" ) ;
			this.question = qt.addNewQuestion() ;			
			String name = getUnqualifiedName( this.variables.get(0).getName() ) ;
			this.question.setName( name ) ;
			this.question.setLabel( getQuestionLabel() ) ;
			buildVariablesForQuestion() ;
			if( log.isTraceEnabled() ) exitTrace( "ChildQuestion.buildQuestion" ) ;
		}
		
	}
	
	private class ParentQuestion extends Question {
		protected ArrayList<ChildQuestion> children = new ArrayList<ChildQuestion>() ;
		
		public void buildQuestion( SectionType st ) {
			super.buildQuestion( st ) ;
			if( log.isTraceEnabled() ) enterTrace( "ParentQuestion.buildQuestion" ) ;
			buildVariablesForQuestion() ;
			Iterator<ChildQuestion> cit = children.listIterator() ;
			while( cit.hasNext() ) {
				cit.next().buildQuestion( this.question ) ;
			}
			
			if( log.isTraceEnabled() ) exitTrace( "ParentQuestion.buildQuestion" ) ;
		}
		
	}
	
	private class Kludge_ObservationsDuringCurrentPeriodOfCareQuestion extends ParentQuestion {
		
		public void buildQuestion( SectionType st ) {
			if( log.isTraceEnabled() ) enterTrace( "Kludge_ObservationsDuringCurrentPeriodOfCareQuestion.buildQuestion" ) ;
			question = st.addNewQuestion() ;
			question.setName( "Observations during current episode of care" ) ;
			question.setLabel( "Observations during current episode of care" ) ;	
			
			org.brisskit.onyxmetadata.stageone.beans.VariableType oiVariable = question.addNewVariable() ;
			oiVariable.setName( "ODCEOC" );
			oiVariable.setType( "text" ) ;
			
			Iterator<ChildQuestion> cit = children.listIterator() ;
			while( cit.hasNext() ) {
				cit.next().buildQuestion( this.question ) ;
			}
			
			if( log.isTraceEnabled() ) exitTrace( "Kludge_ObservationsDuringCurrentPeriodOfCareQuestion.buildQuestion" ) ;
		}
		
	}
	
    private static boolean retrieveArgs( String[] args ) {
        boolean retVal = false ;
        if( args != null && args.length > 0 ) {
            
            for( int i=0; i<args.length; i++ ) {
                
            	if( args[i].startsWith( "-input=" ) ) { 
                    OnyxVariables2Metadata.inDirectoryPath = args[i].substring(7) ;
                }
                else if( args[i].startsWith( "-i=" ) ) { 
                	OnyxVariables2Metadata.inDirectoryPath = args[i].substring(3) ;
                }
                else if( args[i].startsWith( "-output=" ) ) { 
                	OnyxVariables2Metadata.outDirectoryPath = args[i].substring(8) ;
                }
                else if( args[i].startsWith( "-o=" ) ) { 
                	OnyxVariables2Metadata.outDirectoryPath = args[i].substring(3) ;
                }                  
                
            }
            if( OnyxVariables2Metadata.inDirectoryPath != null ) {
                retVal = true ;
            }
        }       
        return retVal ;
    }
	
}
