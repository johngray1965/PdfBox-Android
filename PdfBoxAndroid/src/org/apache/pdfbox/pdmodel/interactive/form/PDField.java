package org.apache.pdfbox.pdmodel.interactive.form;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.COSArrayList;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;

/**
 * This is the superclass for a Field element in a PDF. Based on the COS object model from PDFBox.
 * 
 * @author sug
 * 
 */
public abstract class PDField implements COSObjectable
{

	/**
     * A Ff flag.
     */
    public static final int FLAG_READ_ONLY = 1;
    /**
     * A Ff flag.
     */
    public static final int FLAG_REQUIRED = 1 << 1;
    /**
     * A Ff flag.
     */
    public static final int FLAG_NO_EXPORT = 1 << 2;

    private PDAcroForm acroForm;

    private COSDictionary dictionary;

    /**
     * Constructor.
     * 
     * @param theAcroForm The form that this field is part of.
     */
    public PDField(PDAcroForm theAcroForm)
    {
        acroForm = theAcroForm;
        dictionary = new COSDictionary();
        // no required fields in base field class
    }

    /**
     * Creates a COSField from a COSDictionary, expected to be a correct object definition for a field in PDF.
     * 
     * @param theAcroForm The form that this field is part of.
     * @param field the PDF objet to represent as a field.
     */
    public PDField(PDAcroForm theAcroForm, COSDictionary field)
    {
        acroForm = theAcroForm;
        dictionary = field;
    }
    
    /**
     * Returns the partial name of the field.
     * 
     * @return the name of the field
     */
    public String getPartialName()
    {
        return getDictionary().getString(COSName.T);
    }

    /**
     * This will set the partial name of the field.
     * 
     * @param name The new name for the field.
     */
    public void setPartialName(String name)
    {
        getDictionary().setString(COSName.T, name);
    }
    
    /**
     * Find the field type and optionally do a recursive upward search. Sometimes the fieldtype will be specified on the
     * parent instead of the direct object. This will look at this object for the field type, if none is specified then
     * it will look to the parent if there is a parent. If there is no parent and no field type has been found then this
     * will return null.
     * 
     * @return The field type or null if none was found.
     */
    public String findFieldType()
    {
        return findFieldType(getDictionary());
    }

    private String findFieldType(COSDictionary dic)
    {
        String retval = dic.getNameAsString(COSName.FT);
        if (retval == null)
        {
            COSDictionary parent = (COSDictionary) dic.getDictionaryObject(COSName.PARENT, COSName.P);
            if (parent != null)
            {
                retval = findFieldType(parent);
            }
        }
        return retval;

    }
    
    /**
     * This will get the flags for this field.
     * 
     * @return flags The set of flags.
     */
    public int getFieldFlags()
    {
        int retval = 0;
        COSInteger ff = (COSInteger) getDictionary().getDictionaryObject(COSName.FF);
        if (ff != null)
        {
            retval = ff.intValue();
        }
        return retval;
    }
    
    /**
     * This will get the single associated widget that is part of this field. This occurs when the Widget is embedded in
     * the fields dictionary. Sometimes there are multiple sub widgets associated with this field, in which case you
     * want to use getKids(). If the kids entry is specified, then the first entry in that list will be returned.
     * 
     * @return The widget that is associated with this field.
     * @throws IOException If there is an error getting the widget object.
     */
    public PDAnnotationWidget getWidget() throws IOException
    {
        PDAnnotationWidget retval = null;
        List<COSObjectable> kids = getKids();
        if (kids == null)
        {
            retval = new PDAnnotationWidget(getDictionary());
        }
        else if (kids.size() > 0)
        {
            Object firstKid = kids.get(0);
            if (firstKid instanceof PDAnnotationWidget)
            {
                retval = (PDAnnotationWidget) firstKid;
            }
            else
            {
                retval = ((PDField) firstKid).getWidget();
            }
        }
        else
        {
            retval = null;
        }
        return retval;
    }
    
    /**
     * This will find one of the child elements. The name array are the components of the name to search down the tree
     * of names. The nameIndex is where to start in that array. This method is called recursively until it finds the end
     * point based on the name array.
     * 
     * @param name An array that picks the path to the field.
     * @param nameIndex The index into the array.
     * @return The field at the endpoint or null if none is found.
     * @throws IOException If there is an error creating the field.
     */
    public PDField findKid(String[] name, int nameIndex) throws IOException
    {
        PDField retval = null;
        COSArray kids = (COSArray) getDictionary().getDictionaryObject(COSName.KIDS);
        if (kids != null)
        {
            for (int i = 0; retval == null && i < kids.size(); i++)
            {
                COSDictionary kidDictionary = (COSDictionary) kids.getObject(i);
                if (name[nameIndex].equals(kidDictionary.getString("T")))
                {
                    retval = PDFieldFactory.createField(acroForm, kidDictionary);
                    if (name.length > nameIndex + 1)
                    {
                        retval = retval.findKid(name, nameIndex + 1);
                    }
                }
            }
        }
        return retval;
    }
    
    /**
     * This will get all the kids of this field. The values in the list will either be PDWidget or PDField. Normally
     * they will be PDWidget objects unless this is a non-terminal field and they will be child PDField objects.
     * 
     * @return A list of either PDWidget or PDField objects.
     * @throws IOException If there is an error retrieving the kids.
     */
    public List<COSObjectable> getKids() throws IOException
    {
        List<COSObjectable> retval = null;
        COSArray kids = (COSArray) getDictionary().getDictionaryObject(COSName.KIDS);
        if (kids != null)
        {
            List<COSObjectable> kidsList = new ArrayList<COSObjectable>();
            for (int i = 0; i < kids.size(); i++)
            {
                COSDictionary kidDictionary = (COSDictionary) kids.getObject(i);
                if (kidDictionary == null)
                {
                    continue;
                }
                COSDictionary parent = (COSDictionary) kidDictionary.getDictionaryObject(COSName.PARENT, COSName.P);
                if (kidDictionary.getDictionaryObject(COSName.FT) != null
                        || (parent != null && parent.getDictionaryObject(COSName.FT) != null))
                {
                    kidsList.add(PDFieldFactory.createField(acroForm, kidDictionary));
                }
                else if ("Widget".equals(kidDictionary.getNameAsString(COSName.SUBTYPE)))
                {
                    kidsList.add(new PDAnnotationWidget(kidDictionary));
                }
                else
                {
                    //
                    kidsList.add(PDFieldFactory.createField(acroForm, kidDictionary));
                }
            }
            retval = new COSArrayList<COSObjectable>(kidsList, kids);
        }
        return retval;
    }
    
    /**
     * This will get the acroform that this field is part of.
     * 
     * @return The form this field is on.
     */
    public PDAcroForm getAcroForm()
    {
        return acroForm;
    }
    
    /**
     * This will get the dictionary associated with this field.
     * 
     * @return The dictionary that this class wraps.
     */
    public COSDictionary getDictionary()
    {
        return dictionary;
    }

    /**
     * Convert this standard java object to a COS object.
     * 
     * @return The cos object that matches this Java object.
     */
    public COSBase getCOSObject()
    {
        return dictionary;
    }

}
