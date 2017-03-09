
# Page Management Support

This project provides some add-on features like the following:
- DocumentCopyingPageCopyEventListener handling PageCopyEvent propagated by Hippo Channel Manager, in order to copy all 
the linked documents by the page and its components together, when a page is being copied from a channel.
- DocumentManagementService component to provide document/folder workflow operations  

# Documentation 

Documentation is available at [onehippo-forge.github.io/page-management-support](https://onehippo-forge.github.io/page-management-support)

The documentation is generated by this command:

 > mvn clean site:site
 
The output is in the docs directory; push it and GitHub Pages will serve the site automatically. 
