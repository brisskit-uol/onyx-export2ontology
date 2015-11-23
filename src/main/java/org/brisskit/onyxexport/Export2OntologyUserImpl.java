package org.brisskit.onyxexport;

import org.apache.xmlbeans.XmlObject;
import org.brisskit.onyxmetadata.stageone.beans.QuestionType;


public class Export2OntologyUserImpl implements IExport2Ontology {
	
	public static final String[] SYMPTOMS_ONSET =  { 
		"epi_symponset_cat" ,
		"epi_symponset_table" ,
		"epi_symponset_time_cat"
	} ;

	@Override
	public boolean isExcluded( XmlObject target ) {
		
		if( target instanceof QuestionType ) {
			QuestionType qt = (QuestionType)target ;
			String name = qt.getName() ;
			for( int i=0; i<SYMPTOMS_ONSET.length; i++ ) {
				if( name.contains( SYMPTOMS_ONSET[i] ) ) {
					return true ;
				}
			}		
		}
		return false;
	}

}
