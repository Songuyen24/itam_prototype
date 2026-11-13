package com.company.itam;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.company.itam.workflow.core.dto.WorkflowProgressModels.Group;
import com.company.itam.workflow.core.service.WorkflowProgressService;
class WorkflowSelectionTest {
 @Test void preventsExecutingPendingOrDuplicateGroups(){
  var service=new WorkflowProgressService(null,null);
  var groups=List.of(new Group("a","APPROVED",List.of(1L),List.of(),null,null,null),new Group("b","PENDING",List.of(2L),List.of(),null,null,null));
  assertEquals(List.of(groups.getFirst()),service.requireSelection(groups,List.of("a"),"APPROVED"));
  assertThrows(RuntimeException.class,()->service.requireSelection(groups,List.of("b"),"APPROVED"));
  assertThrows(RuntimeException.class,()->service.requireSelection(groups,List.of("a","a"),"APPROVED"));
  assertThrows(RuntimeException.class,()->service.requireSelection(groups,List.of("missing"),"APPROVED"));
 }
}
