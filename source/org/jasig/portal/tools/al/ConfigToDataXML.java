/**
 * Copyright � 2001 The JA-SIG Collaborative.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the JA-SIG Collaborative
 *    (http://www.jasig.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE JA-SIG COLLABORATIVE "AS IS" AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JA-SIG COLLABORATIVE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 **/


package org.jasig.portal.tools.al;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jasig.portal.EntityIdentifier;
import org.jasig.portal.PortalException;
import org.jasig.portal.RDBMServices;
import org.jasig.portal.groups.IEntityGroup;
import org.jasig.portal.groups.IGroupConstants;
import org.jasig.portal.services.GroupService;
import org.jasig.portal.utils.SAX2FilterImpl;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * A utility class to load pushed fragment configuration into the database
 * used by the alconfg ant target.
 *
 * @author <a href="mailto:pkharchenko@interactivebusiness.com">Peter Kharchenko</a>
 * @author <a href="mailto:mvi@immagic.com">Michael Ivanov</a>
 * @version 1.0
 */
public class ConfigToDataXML {

    static final String configXSL="/org/jasig/portal/tools/al/ConfigToDataXML.xsl";

    public static void main(String[] args) throws Exception {

        String alConfigFile=args[0];
        String outputDataFile=args[1];

        HashMap rNames=new HashMap();

        // compile a table of restriction types
        Connection con=null;
        try {
            con=RDBMServices.getConnection();
            if(con!=null) {
                Statement stmt = con.createStatement();

                String query="SELECT RESTRICTION_TYPE,RESTRICTION_NAME FROM UP_RESTRICTIONS";
                ResultSet rs=stmt.executeQuery(query);
                while(rs.next()) {
                    rNames.put(rs.getString("RESTRICTION_NAME"),rs.getString("RESTRICTION_TYPE"));
                    System.out.println("DEBUG: restriction type mapping "+rs.getString("RESTRICTION_NAME")+" -> "+rs.getString("RESTRICTION_TYPE"));
                }

            } else {
                System.out.println("ERROR: unable to obtain database connection.");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("ERROR: exception raised while reading restriction type mappings:");
            e.printStackTrace();
            System.exit(1);
        } finally {
            if(con!=null) {
                RDBMServices.releaseConnection(con);
            }
        }


        // instantiate transfomer
        SAXTransformerFactory saxTFactory=(SAXTransformerFactory) TransformerFactory.newInstance();

        System.out.println("DEBUG: reading XSLT from url="+ConfigToDataXML.class.getResource(configXSL));

        XMLReader reader = XMLReaderFactory.createXMLReader();
        // for some weird weird reason, the following way of instantiating the parser causes all elements to dissapear ...
        // nothing like a bizzare bug like that to take up your afternoon :(
        //        XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        TransformerHandler thand=saxTFactory.newTransformerHandler(new StreamSource(ConfigToDataXML.class.getResourceAsStream(configXSL)));

        // instantiate filter
        ConfigFilter filter=new ConfigFilter(thand,rNames);

        reader.setContentHandler(filter);
        thand.setResult(new StreamResult(outputDataFile));
        reader.parse(new InputSource(ConfigToDataXML.class.getResourceAsStream(alConfigFile)));

        // Cleaning the database before the DbLoader is called
        (new DbCleaner(filter.getFragmentIds())).cleanTables();

        System.out.println("DEBUG: done.");
    }


     /**
     * Cleans up the tables contained the old data of the fragments to be reloaded
     * It is used before the DbLoader utility is called
     *
     */
    private static class DbCleaner {

      private static Vector fragmentIds;

      public DbCleaner ( Vector fragmentIds ) {
        this.fragmentIds = fragmentIds;
      }

      public static void cleanTables() {
       if ( fragmentIds != null && fragmentIds.size() > 0 ) {
        System.out.println("DEBUG: cleaning tables...");

        Connection con = RDBMServices.getConnection();
        try {

         con.setAutoCommit(false);

         PreparedStatement deleteLayoutStruct = con.prepareStatement("DELETE FROM UP_LAYOUT_STRUCT_AGGR WHERE fragment_id = ?");
         PreparedStatement deleteFragments = con.prepareStatement("DELETE FROM UP_FRAGMENTS WHERE fragment_id = ?");
         PreparedStatement deleteFragmentRestrictions = con.prepareStatement("DELETE FROM UP_FRAGMENT_RESTRICTIONS WHERE fragment_id = ?");
         PreparedStatement deleteFragmentParams = con.prepareStatement("DELETE FROM UP_FRAGMENT_PARAM WHERE fragment_id = ?");
         PreparedStatement deleteOwnerFragment = con.prepareStatement("DELETE FROM UP_OWNER_FRAGMENT WHERE fragment_id = ?");
         PreparedStatement deleteGroupFragment = con.prepareStatement("DELETE FROM UP_GROUP_FRAGMENT WHERE fragment_id = ?");

         for ( int i = 0; i < fragmentIds.size(); i++ ) {
          int fragmentId = Integer.parseInt(fragmentIds.get(i).toString());
          // Setting the parameter - fragment id
          deleteLayoutStruct.setInt(1,fragmentId);
          deleteFragments.setInt(1,fragmentId);
          deleteFragmentRestrictions.setInt(1,fragmentId);
          deleteFragmentParams.setInt(1,fragmentId);
          deleteOwnerFragment.setInt(1,fragmentId);
          deleteGroupFragment.setInt(1,fragmentId);

          // Executing statements
          deleteLayoutStruct.executeUpdate();
          deleteFragments.executeUpdate();
          deleteFragmentRestrictions.executeUpdate();
          deleteFragmentParams.executeUpdate();
          deleteOwnerFragment.executeUpdate();
          deleteGroupFragment.executeUpdate();

         }

         // Commit
         con.commit();

         if ( deleteLayoutStruct != null ) deleteLayoutStruct.close();
         if ( deleteFragments != null ) deleteFragments.close();
         if ( deleteFragmentRestrictions != null ) deleteFragmentRestrictions.close();
         if ( deleteFragmentParams != null ) deleteFragmentParams.close();
         if ( deleteOwnerFragment != null ) deleteOwnerFragment.close();
         if ( deleteGroupFragment != null ) deleteGroupFragment.close();

         if ( con != null ) con.close();

         System.out.println("DEBUG: cleaning done...");

        } catch ( Exception e ) {
            System.out.println ( "DEBUG: " + e.getMessage() );
            e.printStackTrace();
          }
       }
      }

    };



    /**
     * Attempts to determine group key based on a group name.
     * If the group key can not be determined in a unique way, the method will terminate!
     *
     * @param groupName a <code>String</code> value
     * @return a group key
     */
    static String getGroupKey(String groupName) throws Exception {
        EntityIdentifier[] mg=GroupService.searchForGroups(groupName,IGroupConstants.IS,Class.forName("org.jasig.portal.security.IPerson"));
        if(mg!=null && mg.length>0) {
            if(mg.length>1) {
                // multiple matches
                System.out.println("ERROR: group name \""+groupName+"\" matches several existing groups: [Key\tName\tDescription]");
                for(int i=0;i<mg.length;i++) {
                    IEntityGroup g=GroupService.findGroup(mg[i].getKey());
                    System.out.print("\t\""+g.getKey()+"\"\t"+g.getName()+"\"\t"+g.getDescription());
                }
                System.out.println("Please modify config file to specify group key directly (i.e. <group key=\"keyValue\">...)");
                System.exit(1);
            } else {
                System.out.println("DEBUG: group \""+groupName+"\", key=\""+mg[0].getKey()+"\"");
                return mg[0].getKey();
            }
        } else {
            // didnt' match
            System.out.println("ERROR: can not find user group with name \""+groupName+"\" in the database !");
            // try nonexact match
            EntityIdentifier[] mg2=GroupService.searchForGroups(groupName,IGroupConstants.CONTAINS,Class.forName("org.jasig.portal.security.IPerson"));
            if(mg2!=null && mg2.length>0) {
                System.out.print("Possible matches are: [");
                for(int i=0;i<mg2.length;i++) {
                    IEntityGroup g=GroupService.findGroup(mg2[i].getKey());
                    System.out.print("\""+g.getName()+"\" ");
                }
                System.out.println("]");
            }
             throw new PortalException("ERROR: can not find user group with name \""+groupName+"\" in the database !");
        }
        return null;
    }

    /**
     * A filter that will perform the following functions:
     * - intercept and verify restriction names, writing out ids
     * - intercept and verify user group names, writing out ids
     *
     */
    private static class ConfigFilter extends SAX2FilterImpl {
        Map rMap;
        boolean groupMode=false;
        AttributesImpl groupAtts;
        String groupLocalName;
        String groupUri;
        String groupData=null;
        private Vector fragmentIds;

        public ConfigFilter(ContentHandler ch,Map rMap) {
            super(ch);
            this.rMap=rMap;
            fragmentIds= new Vector();
        }

        public Vector getFragmentIds() {
           return fragmentIds;
        }

        public void characters (char ch[], int start, int length) throws SAXException   {
            if(groupMode) {
                // accumulate character data
                String ds=new String(ch,start,length);
                if(groupData==null) {
                    groupData=ds;
                } else {
                    groupData=groupData+ds;
                }
            } else {
                super.characters(ch,start,length);
            }
        }

        public void startElement (String uri, String localName, String qName, Attributes atts) throws SAXException {

             // Adding the fragment id to the vector
            if ( qName.equals("fragment") )
             fragmentIds.add(atts.getValue("id"));

            if(qName.equals("group")) { // this could be made more robust by adding another mode for "groups" element
                groupMode=true;
                groupUri=uri; groupLocalName=localName; groupAtts=new AttributesImpl(atts);

            } else if(qName.equals("restriction")) { // this can also be made more robust by adding another mode for "restrictions" element
                AttributesImpl ai=new AttributesImpl(atts);
                // look up restriction name in the DB
                String restrType;
                if(ai.getIndex("type")!=-1) {
                    // restriction type was specified
                    if(!rMap.containsValue(ai.getValue("type"))) {
                        System.out.println("ERROR: specified restriction type \""+ai.getValue("type")+"\" does not exist ! Either correct the type value, or consider using match by restriction name (i.e. <restriction name=\"priority\" ...)");
                        System.exit(1);
                    } else {
                        if(ai.getIndex("name")!=-1 && rMap.containsKey(ai.getValue("name")) && (!ai.getValue("type").equals((String)rMap.get(ai.getValue("name"))))) {
                            System.out.println("ERROR: specified restriction type \""+ai.getValue("type")+"\" does not match the specified name \""+ai.getValue("name")+"\" in the database. name \""+ai.getValue("name")+"\" matches restriction type \""+(String)rMap.get(ai.getValue("name"))+"\"");
                            System.exit(1);
                        } else {
                            super.startElement(uri,localName,qName,atts);
                        }
                    }
                } else {
                    String restrName=ai.getValue("name");
                    restrType=(String)rMap.get(restrName);
                    if(restrType!=null) {
                        ai.addAttribute(uri,"type","type","CDATA",restrType);
                    } else {
                        System.out.println("ERROR: config file specifies a restriction name \""+restrName+"\" which is not registered with the database!");
                        System.exit(1);
                    }
                    super.startElement(uri,localName,qName,ai);
                }
            } else {
                super.startElement(uri,localName,qName,atts);
            }

        }

        public void endElement (String uri, String localName, String qName)	throws SAXException {

            if(groupMode) {
                if(qName.equals("group")) {
                    if(groupAtts.getIndex("key")==-1) {
                        if(groupData!=null) {
                            String groupKey=null;
                            try {
                                groupKey=getGroupKey(groupData);
                            } catch (Exception e) {
                                System.out.println("ERROR: encountered exception while trying to determine group key for a group name \""+groupData+"\"");
                                e.printStackTrace();
                                System.exit(1);
                            }
                            groupAtts.addAttribute(groupUri,"key","key","CDATA",groupKey);
                            // output group element
                            super.startElement(groupUri,groupLocalName,"group",groupAtts);
                            super.characters(groupData.toCharArray(), 0, groupData.length());
                            super.endElement(groupUri,groupLocalName,"group");
                        } else {
                            System.out.println("ERROR: one of the group elements is empty and no group key has been specified !");
                            System.exit(1);
                        }
                    } else {
                        // check specified group key
                        try {
                            IEntityGroup g=GroupService.findGroup(groupAtts.getValue("key"));
                            if(g!=null) {
                                if(groupData!=null) {
                                    if(g.getName().equals(groupData)) {
                                        System.out.println("DEBUG: group key=\""+groupAtts.getValue("key")+"\" checked out with the name \""+groupData+"\".");
                                        // output group element
                                        super.startElement(groupUri,groupLocalName,"group",groupAtts);
                                        if(groupData!=null) {
                                            super.characters(groupData.toCharArray(), 0, groupData.length());
                                        }
                                        super.endElement(groupUri,groupLocalName,"group");
                                    } else {
                                        System.out.println("ERROR: group key \""+groupAtts.getValue("key")+"\" belongs to a group with a name \""+g.getName()+"\", where the name specified by the config file is \""+groupData+"\". Please fix the config file.");
                                        System.exit(1);
                                    }
                                }
                            } else {
                                System.out.println("ERROR: unable to find a group with a key \""+groupAtts.getValue("key")+"\"! Either correct the key, or consider matching the group by name.");
                                System.exit(1);
                            }
                        } catch (Exception e) {
                            System.out.println("ERROR: exception raised while trying to look up group by a key=\""+groupAtts.getValue("key")+"\"!");
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }
                } else {
                    System.out.println("WARNING: <group/> contains other elements, which it shouldn't! Please check config validity.");
                }
                groupMode=false;
                groupData=null;
                groupAtts=null;
                groupUri=null;
                groupLocalName=null;
            } else {
                super.endElement(uri,localName,qName);
            }
        }
    };
}
