package de.dbuss.tefcontrol.data.service;

import de.dbuss.tefcontrol.data.entity.Constants;
import de.dbuss.tefcontrol.data.modules.pfgproductmapping.entity.ProductHierarchie;
import de.dbuss.tefcontrol.data.repository.ProductHierarchieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductHierarchieService{

    @Autowired
    private JdbcTemplate template;

    private final ProductHierarchieRepository productHierarchieRepository;

    public ProductHierarchieService(ProductHierarchieRepository productHierarchieRepository) {


        this.productHierarchieRepository = productHierarchieRepository;
    }

    public List<ProductHierarchie> findAllProducts(String stringFilter) {
        if (stringFilter == null || stringFilter.isEmpty()) {


            return productHierarchieRepository.findAll();
        } else {
            return productHierarchieRepository.search(stringFilter);
        }
    }

    public long countProducts() {
        return productHierarchieRepository.count();
    }

    public long getMaxID() {
        int anzahl=0;
        try {
            anzahl = template.queryForObject ("select max(ID) from [dbo].[IN_FRONT_CLTV_Product_Hier_PFG]", Integer.class);
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
            return -1;
        }

        return anzahl;
    }

    public void deleteProduct(ProductHierarchie product) {
        productHierarchieRepository.delete(product);
    }

    public void saveProduct(ProductHierarchie product) {
        if (product == null) {

            System.err.println("Product is null. Are you sure you have connected your form to the application?");
            return;
        }


        if (product.getId() == null) {

            product.setId(getMaxID() + 1);
            //product.setId(countProducts() + 1);
            System.out.println("Save new product with ID: " + product.getId());
        }

        productHierarchieRepository.save(product);
    }

    public String startJob(String jobname ){

        try {
            template.execute("msdb.dbo.sp_start_job @job_name=" + jobname);
        }
        catch (Exception e)
        {
            return e.getMessage();
        }

        return Constants.OK;

    }


}
