package de.dbuss.tefcontrol.data.modules.kpi;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Article;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.*;
import de.dbuss.tefcontrol.components.DefaultUtils;
import de.dbuss.tefcontrol.components.LogView;
import de.dbuss.tefcontrol.components.QS_Callback;
import de.dbuss.tefcontrol.components.QS_Grid;
import de.dbuss.tefcontrol.data.dto.ProjectAttachmentsDTO;
import de.dbuss.tefcontrol.data.entity.*;
import de.dbuss.tefcontrol.data.service.*;
import de.dbuss.tefcontrol.security.AuthenticatedUser;
import de.dbuss.tefcontrol.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@PageTitle("Strategic KPI | TEF-Control")
@Route(value = "Strategic_KPI/:project_Id", layout = MainLayout.class)
@RolesAllowed({"ADMIN", "FLIP"})
public class Strategic_KPIView extends VerticalLayout implements BeforeEnterObserver {

    private LogView logView;
    private int projectId;
    private QS_Grid qsGrid;
    private final BackendService backendService;
    private Optional<Projects> projects;
    private List<ProjectAttachmentsDTO> listOfProjectAttachments;
    private Boolean isLogsVisible = false;
    private Boolean isVisible = false;
    Integer errors_Count=0;
    InputStream fileDataFact;
    InputStream fileDataDim;

    String fileName = "";
    Article article = new Article();
    Div textArea = new Div();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    long contentLength = 0;
    String mimeType = "";
    Integer errors_Fact=0;
    Grid<ProjectParameter> parameterGrid = new Grid<>(ProjectParameter.class, false);

    private final ProjectsService projectsService;
    private final ProjectAttachmentsService projectAttachmentsService;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private String agentName;
    private String factTableName;
    private String dimTableName;
    MemoryBuffer memoryBuffer = new MemoryBuffer();
    Upload singleFileUpload = new Upload(memoryBuffer);

    private Button uploadBtn;
    private int upload_id;
    private DefaultUtils defaultUtils;

    private AuthenticatedUser authenticatedUser;
    private final ProjectConnectionService projectConnectionService;
    private JdbcTemplate jdbcTemplate;
    private List<Fact_CC_KPI> listOfFact_CC_KPI = new ArrayList<Fact_CC_KPI>();
    private List<Dim_CC_KPI> listOfDim_CC_KPI = new ArrayList<Dim_CC_KPI>();
    public Strategic_KPIView(JdbcTemplate jdbcTemplate, ProjectConnectionService projectConnectionService, ProjectParameterService projectParameterService, ProjectsService projectsService, ProjectAttachmentsService projectAttachmentsService, AuthenticatedUser authenticatedUser, BackendService backendService) {

        this.jdbcTemplate = jdbcTemplate;
        this.projectsService = projectsService;
        this.projectAttachmentsService = projectAttachmentsService;
        this.authenticatedUser = authenticatedUser;
        this.projectConnectionService = projectConnectionService;
        this.backendService = backendService;

        logView = new LogView();
        logView.logMessage(Constants.INFO, "Starting Strategic_KPIView");

        uploadBtn = new Button("QS and Start Job");
        uploadBtn.setEnabled(false);

        List<ProjectParameter> listOfProjectParameters = projectParameterService.findAll();
        List<ProjectParameter> filteredProjectParameters = listOfProjectParameters.stream()
                .filter(projectParameter -> Constants.Strategic_KPI.equals(projectParameter.getNamespace()))
                .collect(Collectors.toList());

        String dbServer = null;
        String dbName = null;

        for (ProjectParameter projectParameter : filteredProjectParameters) {
            //  if(projectParameter.getNamespace().equals(Constants.TECH_KPI)) {
            if (Constants.DB_SERVER.equals(projectParameter.getName())) {
                dbServer = projectParameter.getValue();
            } else if (Constants.DB_NAME.equals(projectParameter.getName())) {
                dbName = projectParameter.getValue();
            } else if (Constants.DB_USER.equals(projectParameter.getName())) {
                dbUser = projectParameter.getValue();
            } else if (Constants.DB_PASSWORD.equals(projectParameter.getName())) {
                dbPassword = projectParameter.getValue();
            }
            else if (Constants.FACT_TABLE.equals(projectParameter.getName())) {
                factTableName = projectParameter.getValue();
            }
            else if (Constants.DIM_TABLE.equals(projectParameter.getName())) {
                dimTableName = projectParameter.getValue();
            }
            else if (Constants.DB_JOBS.equals(projectParameter.getName())) {
                agentName = projectParameter.getValue();
            }
            // }
        }
        dbUrl = "jdbc:sqlserver://" + dbServer + ";databaseName=" + dbName + ";encrypt=true;trustServerCertificate=true";

        setProjectParameterGrid(filteredProjectParameters);
        defaultUtils = new DefaultUtils(projectsService, projectAttachmentsService);

        qsGrid = new QS_Grid(projectConnectionService, backendService);

        HorizontalLayout hl = new HorizontalLayout();
        hl.add(getTabsheet());
        hl.setHeightFull();
        hl.setSizeFull();

        textArea.setClassName("Info");
        textArea.add("please upload Excel file...");

        setHeightFull();
        setSizeFull();
        getStyle().set("overflow", "auto");
        add(hl, parameterGrid);

        parameterGrid.setVisible(false);
        logView.setVisible(false);
        add(logView);
        if(MainLayout.isAdmin) {
            UI.getCurrent().addShortcutListener(
                    () -> {
                        isLogsVisible = !isLogsVisible;
                        logView.setVisible(isLogsVisible);
                    },
                    Key.KEY_V, KeyModifier.ALT);
            UI.getCurrent().addShortcutListener(
                    () -> {
                        isVisible = !isVisible;
                        parameterGrid.setVisible(isVisible);
                    },
                    Key.KEY_I, KeyModifier.ALT);
        }
        logView.logMessage(Constants.INFO, "Ending Tech_KPIView");




    }

    private void setProjectParameterGrid(List<ProjectParameter> listOfProjectParameters) {
        logView.logMessage(Constants.INFO, "Starting setProjectParameterGrid() for set database detail in Grid");
        parameterGrid = new Grid<>(ProjectParameter.class, false);
        parameterGrid.addColumn(ProjectParameter::getName).setHeader("Name").setAutoWidth(true).setResizable(true);
        parameterGrid.addColumn(ProjectParameter::getValue).setHeader("Value").setAutoWidth(true).setResizable(true);
        parameterGrid.addColumn(ProjectParameter::getDescription).setHeader("Description").setAutoWidth(true).setResizable(true);

        parameterGrid.setItems(listOfProjectParameters);
        parameterGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        parameterGrid.setHeight("200px");
        parameterGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        logView.logMessage(Constants.INFO, "Ending setProjectParameterGrid() for set database detail in Grid");
    }

    private TabSheet getTabsheet() {
        logView.logMessage(Constants.INFO, "Starting getTabsheet() for Tabs");
        //log.info("Starting getTabsheet() for Tabsheet");
        TabSheet tabSheet = new TabSheet();

        tabSheet.add("Upload", getUpladTab());
        tabSheet.add("Description", getDescriptionTab());
        tabSheet.add("Attachments", getAttachmentTab());

        tabSheet.setSizeFull();
        tabSheet.setHeightFull();
        //log.info("Ending getTabsheet() for Tabsheet");
        logView.logMessage(Constants.INFO, "Ending getTabsheet() for Tabs");
        return tabSheet;
    }

    private Component getUpladTab() {
        logView.logMessage(Constants.INFO, "Sarting getUpladTab() for set upload data");
        VerticalLayout content = new VerticalLayout();

        setupUploader();

        HorizontalLayout hl=new HorizontalLayout(singleFileUpload, qsGrid);
        content.add(hl, textArea, uploadBtn, parameterGrid);


        uploadBtn.addClickListener(e->{


            //Aufruf QS-Grid:
            logView.logMessage(Constants.INFO, "executing QS-Grid");
            //   if (qsGrid.projectId != projectId) {
            hl.remove(qsGrid);
            qsGrid = new QS_Grid(projectConnectionService, backendService);
            hl.add(qsGrid);
            CallbackHandler callbackHandler = new CallbackHandler();
            qsGrid.createDialog(callbackHandler, projectId, upload_id);
            //   }
            qsGrid.showDialog(true);





        });


        content.setSizeFull();
        content.setHeightFull();
        content.getStyle().set("overflow", "auto");


        return content;
    }

    public class CallbackHandler implements QS_Callback {
        // Die Methode, die aufgerufen wird, wenn die externe Methode abgeschlossen ist
        @Override
        public void onComplete(String result) {
            logView.logMessage(Constants.INFO, "Starting CallbackHandler onComplete for execute Start Job");
            if(!result.equals("Cancel")) {
                qsGrid.executeStartJobSteps(upload_id, agentName);
                article=new Article();
                article.setText("Job" + agentName + " started...");
                textArea.add(article);

            }
            else
            {
                article=new Article();
                article.setText("Job" + agentName + " not started");
                textArea.add(article);
            }

            logView.logMessage(Constants.INFO, "Ending CallbackHandler onComplete for execute Start Job");
        }
    }


    private String saveEntities() {
        logView.logMessage(Constants.INFO, "Starting saveFactEntities() for saving Fact file data in database");
        AtomicReference<String> returnStatus= new AtomicReference<>("false");
        int totalRows = listOfFact_CC_KPI.size();

        //System.out.println("Upload Data to DB");

        projectConnectionService.getJdbcConnection(dbUrl, dbUser, dbPassword);

        String resultKPIFact = projectConnectionService.saveStrategic_KPIFact(listOfFact_CC_KPI, factTableName, upload_id);
        returnStatus.set(resultKPIFact);

        if (returnStatus.toString().equals(Constants.OK)){
            logView.logMessage(Constants.INFO, "ResultKPIFact: " + returnStatus.toString());
        }
        else{
            logView.logMessage(Constants.ERROR, "ERROR: " + returnStatus.toString());
            textArea.setClassName("Error");
            return "Data not save to db: " +  returnStatus.toString();
        }

        String resultKPIDim = projectConnectionService.saveStrategic_KPIDim(listOfDim_CC_KPI, dimTableName, upload_id);
        returnStatus.set(resultKPIDim);

        //System.out.println("ResultKPIDim: " + returnStatus.toString());
        logView.logMessage(Constants.INFO, "ResultKPIDim: " + returnStatus.toString());

        if (returnStatus.toString().equals(Constants.OK)){
            logView.logMessage(Constants.INFO, "ResultKPIFact: " + returnStatus.toString());
        }
        else{

            logView.logMessage(Constants.ERROR, "ERROR: " + returnStatus.toString());
            textArea.setClassName("Error");
            return "Data not save to db: " +  returnStatus.toString();
        }


        logView.logMessage(Constants.INFO, "Ending saveFactEntities() for saving Fact file data in database");
        return "Data with upload_id " + upload_id + " saved successfully to db...";
    }

    private void setupUploader() {
        logView.logMessage(Constants.INFO, "Starting setupUploader() for setup file uploader");
        singleFileUpload.setWidth("450px");

        singleFileUpload.addStartedListener(e->{
            errors_Count=0;
           //textArea.setText("");
            uploadBtn.setEnabled(false);
            //qsBtn.setEnabled(false);
        });

        singleFileUpload.addSucceededListener(event -> {
            logView.logMessage(Constants.INFO, "File Uploaded: >" + event.getFileName() + "<");

            textArea.removeAll();
            textArea.setClassName("Info");

            // Get information about the uploaded file
            fileDataFact = memoryBuffer.getInputStream();
            fileDataDim = memoryBuffer.getInputStream();

            fileName = event.getFileName();
            contentLength = event.getContentLength();
            mimeType = event.getMIMEType();

            article=new Article();
            article.setText("Uploaded File: >>" + fileName + "<< (Size: " + contentLength/1024 + " KB)");
            textArea.add(article);

            logView.logMessage(Constants.INFO, "contentLenght: >" + contentLength + "<");
            logView.logMessage(Constants.INFO, "mimeType: >" + mimeType + "<");

            singleFileUpload.clearFileList();

            listOfFact_CC_KPI = parseExcelFile_Fact(fileDataFact, fileName,"Fact_CC_KPI");
            listOfDim_CC_KPI = parseExcelFile_Dim(fileDataDim, fileName,"DIM_CC_KPI");

            if (listOfFact_CC_KPI == null || listOfDim_CC_KPI == null ){
                article=new Article();
                article.setText("Error: no Sheet with name >>Fact_CC_KPI<< or >>DIM_CC_KPI<<found!");
                textArea.add(article);
                textArea.setClassName("Error");
                return;
            }


            logView.logMessage(Constants.INFO, "error_Count: " + errors_Count);

            if (errors_Count==0)
            {
                logView.logMessage(Constants.INFO, "Uploading in uploadBtn.addClickListener");
                //    ui.setPollInterval(500);

                ProjectUpload projectUpload = new ProjectUpload();
                projectUpload.setFileName(fileName);
                //projectUpload.setUserName(MainLayout.userName);
                Optional<User> maybeUser = authenticatedUser.get();
                if (maybeUser.isPresent()) {
                    User user = maybeUser.get();
                    projectUpload.setUserName(user.getUsername());
                }
                projectUpload.setModulName("Strategic_KPI");

                logView.logMessage(Constants.INFO, "Get file upload id from database");
                projectConnectionService.getJdbcConnection(dbUrl, dbUser, dbPassword); // Set Connection to target DB
                upload_id = projectConnectionService.saveUploadedGenericFileData(projectUpload);

                if (upload_id == -1){
                    article=new Article();
                    article.setText("Error: could not generate upload_id !");
                    textArea.add(article);
                    textArea.setClassName("Error");
                    return;
                }

                projectUpload.setUploadId(upload_id);

                logView.logMessage(Constants.INFO, "upload id: " + upload_id);
                System.out.println("Upload_ID: " + upload_id);

                String erg= saveEntities();

                if (erg.contains("successfully") )
                {
                    uploadBtn.setEnabled(true);
                }

                Paragraph p = new Paragraph(erg);
                textArea.add(p);



            }
            else {
                Paragraph p = new Paragraph("data not saved to db");
                textArea.add(p);
                textArea.setClassName("Error");

            }

        });
        System.out.println("setup uploader................over");
        logView.logMessage(Constants.INFO, "Ending setupUploader() for setup file uploader");
    }

    public List<Fact_CC_KPI> parseExcelFile_Fact(InputStream fileData, String fileName, String sheetName) {
        logView.logMessage(Constants.INFO, "Starting parseExcelFile_Fact() for parse uploaded file");

        List<Fact_CC_KPI> listOfKPI_Fact = new ArrayList<>();
        try {
            if(fileName.isEmpty() || fileName.length()==0)
            {
                article=new Article();
                article.setText("Error: Keine Datei angegeben!");
                textArea.add(article);
            }

            if(!mimeType.contains("openxmlformats-officedocument"))
            {
                article=new Article();
                article.setText("Error: ungültiges Dateiformat!");
                textArea.add(article);
            }

            System.out.println("Excel import: "+  fileName + " => Mime-Type: " + mimeType  + " Größe " + contentLength + " Byte");

            //  HSSFWorkbook my_xls_workbook = new HSSFWorkbook(fileData);
            XSSFWorkbook my_xls_workbook = new XSSFWorkbook(fileData);
            //   HSSFSheet my_worksheet = my_xls_workbook.getSheetAt(0);
            XSSFSheet my_worksheet = my_xls_workbook.getSheet(sheetName);

            if (my_worksheet == null){
                return null;
            }

            Iterator<Row> rowIterator = my_worksheet.iterator();

            Integer RowNumber=0;
            errors_Fact=0;

            while(rowIterator.hasNext() )
            {
                Fact_CC_KPI kPI_Fact = new Fact_CC_KPI();
                Row row = rowIterator.next();
                RowNumber++;

                if (errors_Fact >0 | errors_Count!=0){
                    errors_Count++;
                    article=new Article();
                    article.setText("Abort further processing...");
                    textArea.add(article);
                    return null;
                }

                Iterator<Cell> cellIterator = row.cellIterator();

                while(cellIterator.hasNext()) {

                    if(RowNumber==1 ) //Überschrift nicht betrachten, aber Anzahl Spalten kontrollieren
                    {
                        if (row.getLastCellNum()<5)
                        {
                            article=new Article();
                            article.setText(LocalDateTime.now().format(formatter) + ": Error: Count Columns: " + row.getLastCellNum() + " Expected: 5! (Period | Scenario | Segment | CC_KPI | Amount)");
                            textArea.add(article);
                            errors_Fact=1;
                        }

                        break;
                    }


                    Cell cell = cellIterator.next();
                    kPI_Fact.setRow(RowNumber);

                    if(cell.getColumnIndex()==0)
                    {
                        String ColumnName="Period";
                        try {
                            //kPI_Fact.setPeriod(checkCellNumeric(sheetName, cell, RowNumber,ColumnName));
                            kPI_Fact.setPeriod(defaultUtils.getCellNumeric(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("Sheet: " + sheetName + ": Error: Zeile " + RowNumber.toString() + ", Spalte " + ColumnName + ": " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;
                        }
                    }

                    if(cell.getColumnIndex()==1)
                    {
                        String ColumnName="Scenario";
                        try {
                            //kPI_Fact.setScenario(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            //kPI_Fact.setScenario(getCellString(cell));

                            kPI_Fact.setScenario(defaultUtils.getCellString(cell));

                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("Sheet: " + sheetName + ": Error: Zeile " + RowNumber.toString() + ", Spalte " + ColumnName + ": " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }
                    if(cell.getColumnIndex()==2)
                    {
                        String ColumnName="Segment";
                        try {
                            //kPI_Fact.setSegment(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Fact.setSegment(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("Sheet: " + sheetName + ": Error: Zeile " + RowNumber.toString() + ", Spalte " + ColumnName + ": " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                    if(cell.getColumnIndex()==3)
                    {
                        String ColumnName="CC_KPI";
                        try {
                            //kPI_Fact.setCC_KPI(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Fact.setCC_KPI(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("Sheet: " + sheetName + ": Error: Zeile " + RowNumber.toString() + ", Spalte " + ColumnName + ": " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                    if(cell.getColumnIndex()==4)
                    {
                        String ColumnName="Amount";
                        try {
                            //kPI_Fact.setAmount(checkCellDouble(sheetName, cell, RowNumber,ColumnName));
                            kPI_Fact.setAmount(defaultUtils.getCellDouble(cell));
                        }

                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("Sheet: " + sheetName + ": Error: Zeile " + RowNumber.toString() + ", Spalte " + ColumnName + ": " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                }

                if(kPI_Fact.isValid() )
                {
                    listOfKPI_Fact.add(kPI_Fact);
                }
                else
                {
                    System.out.println("Fact: skip empty row : " + kPI_Fact.getRow());
                }


            }


            article=new Article();
            article.setText("Count rows sheet: " + sheetName + " => " + listOfKPI_Fact.size());
            textArea.add(article);


            errors_Count+=errors_Fact;
            logView.logMessage(Constants.INFO, "Ending parseExcelFile_Fact() for parse uploaded file");
            return listOfKPI_Fact;

        } catch (Exception e) {
            article=new Article();
            article.setText("Error while parse file!: " + e.getMessage());
            textArea.add(article);
            logView.logMessage(Constants.ERROR, "Error while parse uploaded file");
            textArea.setClassName("Error");
            e.printStackTrace();
            return null;
        }

    }
    public List<Dim_CC_KPI> parseExcelFile_Dim(InputStream fileData, String fileName, String sheetName) {
        logView.logMessage(Constants.INFO, "Starting parseExcelFile_Dim() for parse uploaded file");

        List<Dim_CC_KPI> listOfKPI_Dim = new ArrayList<>();
        try {
            if(fileName.isEmpty() || fileName.length()==0)
            {
                  article=new Article();
                  article.setText(LocalDateTime.now().format(formatter) + ": Error: Keine Datei angegeben!");
                  textArea.add(article);
            }

            if(!mimeType.contains("openxmlformats-officedocument"))
            {
                  article=new Article();
                  article.setText(LocalDateTime.now().format(formatter) + ": Error: ungültiges Dateiformat!");
                  textArea.add(article);
            }

            System.out.println("Excel import: "+  fileName + " => Mime-Type: " + mimeType  + " Größe " + contentLength + " Byte");
            //textArea.setText(LocalDateTime.now().format(formatter) + ": Info: Verarbeite Datei: " + fileName + " (" + contentLength + " Byte)");
            //message.setText(LocalDateTime.now().format(formatter) + ": Info: reading file: " + fileName);

            //addRowsBT.setEnabled(false);
            //replaceRowsBT.setEnabled(false);
            //spinner.setVisible(true);

            //  HSSFWorkbook my_xls_workbook = new HSSFWorkbook(fileData);
            XSSFWorkbook my_xls_workbook = new XSSFWorkbook(fileData);
            //   HSSFSheet my_worksheet = my_xls_workbook.getSheetAt(0);
            XSSFSheet my_worksheet = my_xls_workbook.getSheet(sheetName);
            Iterator<Row> rowIterator = my_worksheet.iterator();

            Integer RowNumber=0;
            errors_Fact=0;

            while(rowIterator.hasNext() )
            {
                Dim_CC_KPI kPI_Dim = new Dim_CC_KPI();
                Row row = rowIterator.next();
                RowNumber++;

                if (errors_Fact > 0 | errors_Count != 0) {
                    article=new Article();
                    article.setText("Abort further processing...");
                    textArea.add(article);
                    return null;
                    //break;
                }

                Iterator<Cell> cellIterator = row.cellIterator();

                while(cellIterator.hasNext()) {

                    if(RowNumber==1 ) //Überschrift nicht betrachten, aber Anzahl Spalten kontrollieren
                    {


                        if (row.getLastCellNum()<3)
                        {
                            article=new Article();
                            article.setText(LocalDateTime.now().format(formatter) + ": Error: Count Columns: " + row.getLastCellNum() + " Expected: 3! (CC_KPI | CC_KPI_Gen01 | CC_KPI_Gen02)");
                            textArea.add(article);
                            errors_Fact=1;
                        }

                        break;
                    }


                    Cell cell = cellIterator.next();
                    kPI_Dim.setRow(RowNumber);

                    if(cell.getColumnIndex()==0)
                    {
                        String ColumnName="CC_KPI";
                        try {
                            //kPI_Dim.setCC_KPI(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Dim.setCC_KPI(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("ERROR: Sheet: >>" + sheetName + "<< row: " + RowNumber.toString() + ", column " + ColumnName + " => " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                    if(cell.getColumnIndex()==1)
                    {
                        String ColumnName="CC_KPI_Sort";
                        try {
                            //kPI_Dim.setCC_KPI(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Dim.setCC_KPI_Sort(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("ERROR: Sheet: >>" + sheetName + "<< row: " + RowNumber.toString() + ", column " + ColumnName + " => " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                    if(cell.getColumnIndex()==2)
                    {
                        String ColumnName="CC_KPI_Gen01";
                        try {
                            //kPI_Dim.setCC_KPI_Gen01(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Dim.setCC_KPI_Gen01(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("ERROR: Sheet: >>" + sheetName + "<< row: " + RowNumber.toString() + ", column " + ColumnName + " => " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }
                    if(cell.getColumnIndex()==3)
                    {
                        String ColumnName="CC_KPI_Gen02";
                        try {
                            //kPI_Dim.setCC_KPI_Gen02(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Dim.setCC_KPI_Gen02(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("ERROR: Sheet: >>" + sheetName + "<< row: " + RowNumber.toString() + ", column " + ColumnName + " => " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                    if(cell.getColumnIndex()==4)
                    {
                        String ColumnName="Unit";
                        try {
                            //kPI_Dim.setCC_KPI_Gen02(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Dim.setUnit(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("ERROR: Sheet: >>" + sheetName + "<< row: " + RowNumber.toString() + ", column " + ColumnName + " => " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                    if(cell.getColumnIndex()==5)
                    {
                        String ColumnName="Definition";
                        try {
                            //kPI_Dim.setCC_KPI_Gen02(checkCellString(sheetName, cell, RowNumber,ColumnName));
                            kPI_Dim.setDefinition(defaultUtils.getCellString(cell));
                        }
                        catch(Exception e)
                        {
                            article=new Article();
                            article.setText("ERROR: Sheet: >>" + sheetName + "<< row: " + RowNumber.toString() + ", column " + ColumnName + " => " + e.getMessage());
                            textArea.add(article);
                            errors_Fact++;

                        }
                    }

                }

                if(kPI_Dim.isValid() )
                {
                    listOfKPI_Dim.add(kPI_Dim);
                }
                else
                {
                    System.out.println("Fact: skip empty row : " + kPI_Dim.getRow());
                }


            }

            System.out.println("Anzahl Zeilen im Excel: " + listOfKPI_Dim.size());

            article=new Article();
            article.setText("Count rows sheet: " + sheetName + " => " + listOfKPI_Dim.size());
            textArea.add(article);

            errors_Count+=errors_Fact;
            logView.logMessage(Constants.INFO, "Ending parseExcelFile_Dim() for parse uploaded file");
            return listOfKPI_Dim;

        } catch (Exception e) {
            article=new Article();
            article.setText("Error while parse file!: " + e.getMessage());
            textArea.add(article);
            logView.logMessage(Constants.ERROR, "Error while parse uploaded file");
            e.printStackTrace();
            return null;
        }

    }

    private Component getDescriptionTab() {
        logView.logMessage(Constants.INFO, "Set Description in getDescriptionTab()");
        return defaultUtils.getProjectDescription();
    }

    private Component getAttachmentTab() {
        logView.logMessage(Constants.INFO, "Set Attachment in getAttachmentTab()");
        return defaultUtils.getProjectAttachements();
    }

    private void updateAttachmentGrid(List<ProjectAttachmentsDTO> projectAttachmentsDTOS) {
        logView.logMessage(Constants.INFO, "Update Description in updateAttachmentGrid()");
        defaultUtils.setProjectId(projectId);
        defaultUtils.setAttachmentGridItems(projectAttachmentsDTOS);
    }
    private void updateDescription() {
        logView.logMessage(Constants.INFO, "Update Attachment in updateDescription()");
        defaultUtils.setProjectId(projectId);
        defaultUtils.setDescription();
    }
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        logView.logMessage(Constants.INFO, "Starting beforeEnter() for update");
        RouteParameters parameters = event.getRouteParameters();
        projectId = Integer.parseInt(parameters.get("project_Id").orElse(null));
        projects = projectsService.findById(projectId);
        projects.ifPresent(value -> listOfProjectAttachments = projectsService.getProjectAttachmentsWithoutFileContent(value));

        updateDescription();
        updateAttachmentGrid(listOfProjectAttachments);
        logView.logMessage(Constants.INFO, "Ending beforeEnter() for update");
    }

    public class Fact_CC_KPI {

        private int row;
        private int Period ;
        private String Scenario = "";
        private String Segment = "";
        private String CC_KPI = "";
        private Double Amount;

        public int getPeriod(){return Period;};
        public void setPeriod(int period){this.Period=period;}
        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getSegment() {
            return Segment;
        }

        public void setSegment(String Segment) {
            this.Segment = Segment;
        }

        public String getCC_KPI() {
            return CC_KPI;
        }

        public void setCC_KPI(String CC_KPI) {
            this.CC_KPI = CC_KPI;
        }


        public String getScenario() {
            return Scenario;
        }

        public void setScenario(String scenario) {
            Scenario = scenario;
        }

        public Double getAmount() {return Amount;}

        public void setAmount(Double amount) {
            Amount = amount;
        }

        public boolean isValid() {
            if (Segment == null || Segment.isEmpty()) {
                if (Scenario == null || Scenario.isEmpty()) {
                    if (CC_KPI == null || CC_KPI.isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        }

    }

    public class Dim_CC_KPI {

        private int row;
        private String CC_KPI = "";
        private String CC_KPI_Sort = "";
        private String CC_KPI_Gen01 = "";
        private String CC_KPI_Gen02 = "";
        private String Unit = "";
        private String Definition = "";

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getCC_KPI() {
            return CC_KPI;
        }

        public void setCC_KPI_Sort(String CC_KPI_Sort) {
            this.CC_KPI_Sort = CC_KPI_Sort;
        }

        public String getCC_KPI_Sort() {
            return CC_KPI_Sort;
        }

        public void setCC_KPI(String CC_KPI) {
            this.CC_KPI = CC_KPI;
        }

        public String getCC_KPI_Gen01() {
            return CC_KPI_Gen01;
        }

        public void setCC_KPI_Gen01(String cc_kpi_gen01) {
            CC_KPI_Gen01 = cc_kpi_gen01;
        }

        public String getCC_KPI_Gen02() {
            return CC_KPI_Gen02;
        }

        public String getUnit() {
            return Unit;
        }

        public void setUnit(String unit) {
            Unit = unit;
        }

        public String getDefinition() {
            return Definition;
        }

        public void setDefinition(String definition) {
            Definition = definition;
        }

        public void setCC_KPI_Gen02(String cc_kpi_gen02) {
            CC_KPI_Gen02 = cc_kpi_gen02;
        }
        public boolean isValid() {
            if (CC_KPI == null || CC_KPI.isEmpty()) {
                if (CC_KPI_Gen01 == null || CC_KPI_Gen01.isEmpty()) {
                    if (CC_KPI == null || CC_KPI.isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        }

    }


}
