package org.diylc.editor;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.HashSet;
import java.util.Set;

import org.diylc.common.IProjectEditor;
import org.diylc.common.Orientation;
import org.diylc.components.AbstractCurvedComponent.PointCount;
import org.diylc.components.AbstractLeadedComponent;
import org.diylc.components.connectivity.AWG;
import org.diylc.components.connectivity.HookupWire;
import org.diylc.components.guitar.AbstractGuitarPickup;
import org.diylc.components.guitar.SingleCoilPickup;
import org.diylc.components.passive.PotentiometerPanel;
import org.diylc.components.passive.PotentiometerPanel.Type;
import org.diylc.components.semiconductors.AbstractTransistorPackage;
import org.diylc.components.semiconductors.TransistorTO126;
import org.diylc.components.semiconductors.TransistorTO220;
import org.diylc.components.tube.TubeSocket;
import org.diylc.components.tube.TubeSocket.Mount;
import org.diylc.core.IDIYComponent;
import org.diylc.core.Project;
import org.diylc.core.measures.Size;
import org.diylc.core.measures.SizeUnit;
import org.diylc.presenter.InstantiationManager;

public class FlexibleLeadsEditor implements IProjectEditor {
  
  private InstantiationManager instantiationManager;

  @Override
  public Set<IDIYComponent<?>> edit(Project project, Set<IDIYComponent<?>> selection) {
    Set<IDIYComponent<?>> newSelection = new HashSet<IDIYComponent<?>>(selection);
    for (IDIYComponent<?> c : selection) {
      if (c instanceof AbstractLeadedComponent<?>)
        addLeads((AbstractLeadedComponent<?>) c, project, newSelection);
      else if (c instanceof AbstractTransistorPackage)
        addLeads((AbstractTransistorPackage) c, project, newSelection);
      else if (c instanceof PotentiometerPanel)
        addLeads((PotentiometerPanel) c, project, newSelection);
      else if (c instanceof TubeSocket)
        addLeads((TubeSocket) c, project, newSelection);
      else if (c instanceof AbstractGuitarPickup)
        addLeads((AbstractGuitarPickup) c, project, newSelection);
    }
    
    return newSelection;
  }
  
  private void addLeads(AbstractLeadedComponent<?> leaded, Project project, Set<IDIYComponent<?>> newSelection) {
      Size l = leaded.getLength();
      // do not apply to jumpers, etc
      if (l == null)
        return;
      
      Point p1 = new Point(leaded.getControlPoint(0));
      Point p2 = new Point(leaded.getControlPoint(1));
      double d = p1.distance(p2);
      double len = l.convertToPixels() + 2;
              
      double diff = (d - len)  / 2;
      
      if (diff < 20)
        return;
      
      // find the center and angle
      double centerX = (p1.x + p2.x) / 2.0;
      double centerY = (p1.y + p2.y) / 2.0;
      double theta = Math.atan2(p2.y - p1.y, p2.x - p1.x);
      // shorten the component to match the size of the body
      Point newP1 = new Point((int)Math.round(centerX - Math.cos(theta) * len / 2), (int)Math.round(centerY - Math.sin(theta) * len / 2));
      Point newP2 = new Point((int)Math.round(centerX + Math.cos(theta) * len / 2), (int)Math.round(centerY + Math.sin(theta) * len / 2));
      leaded.setControlPoint(newP1, 0);
      leaded.setControlPoint(newP2, 1);
             
      // create leads
      HookupWire w1 = new HookupWire();
      getInstantiationManager().fillWithDefaultProperties(w1, null);      
      w1.setGauge(AWG._26);
      w1.setLeadColor(leaded.getLeadColor());
//      w1.setPointCount(PointCount.THREE);
      w1.setControlPoint(p1, 0);
      for (int i = 1; i < w1.getControlPointCount() - 1; i++) {
        double r = (len + diff) / (w1.getControlPointCount() - 1) * (w1.getControlPointCount() - i - 1);
        w1.setControlPoint(new Point((int)Math.round(centerX - Math.cos(theta) * r), (int)Math.round(centerY - Math.sin(theta) * r)), i);   
      }
      w1.setControlPoint(newP1, w1.getControlPointCount() - 1);
      newSelection.add(w1);
      
      HookupWire w2 = new HookupWire();
      getInstantiationManager().fillWithDefaultProperties(w2, null);
      w2.setGauge(AWG._26);
      w2.setLeadColor(leaded.getLeadColor());
//      w2.setPointCount(PointCount.THREE);
      w2.setControlPoint(p2, 0);
      for (int i = 1; i < w2.getControlPointCount() - 1; i++) {
        double r = (len + diff) / (w2.getControlPointCount() - 1) * (w2.getControlPointCount() - i - 1);
        w2.setControlPoint(new Point((int)Math.round(centerX + Math.cos(theta) * r), (int)Math.round(centerY + Math.sin(theta) * r)), i);   
      }   
      w2.setControlPoint(newP2, w2.getControlPointCount() - 1);
      newSelection.add(w2);
      
      // inject leads right before the component
      int index = project.getComponents().indexOf(leaded);
      project.getComponents().add(index, w1);
      project.getComponents().add(index, w2);    
  }
  
  private void addLeads(AbstractTransistorPackage c, Project project, Set<IDIYComponent<?>> newSelection) {
    Size offsetX = new Size(0.4d, SizeUnit.in);
    Size offsetY = new Size(0.1d, SizeUnit.in);        
    
    // create leads
    for (int i = 0; i < 3; i++) {
      Point p0 = c.getControlPoint(i);
      double dx = -offsetX.convertToPixels();
      double dy = offsetY.convertToPixels() * (i - 1);
      
      // calculate lead position
      Point p1 = new Point((int)(p0.x + dx / 2), (int)(p0.y + dy / 2));
      Point p2 = new Point((int)(p0.x + dx), (int)(p0.y + dy));
      
      if (c.getOrientation() != Orientation.DEFAULT) {
        AffineTransform tx = AffineTransform.getRotateInstance(c.getOrientation().toRadians(), p0.x, p0.y);
        tx.transform(p1, p1);
        tx.transform(p2, p2);
      }
      
      HookupWire w = new HookupWire();
      getInstantiationManager().fillWithDefaultProperties(w, null);
      // make the leads thicker for TO220 and TO126
      if (c instanceof TransistorTO220 || c instanceof TransistorTO126)
        w.setGauge(AWG._24);
      else
        w.setGauge(AWG._26);
      w.setLeadColor(AbstractTransistorPackage.METAL_COLOR);
      w.setPointCount(PointCount.THREE);
      w.setControlPoint(p0, 0);      
      w.setControlPoint(p1, 1);   
      w.setControlPoint(p2, 2);   
      newSelection.add(w);    
      
      // inject leads right before the component
      int index = project.getComponents().indexOf(c);
      project.getComponents().add(index, w);
    }
    
    if (c instanceof TransistorTO220) {
      TransistorTO220 t = (TransistorTO220)c;
      if (t.getFolded())
        t.setLeadLength(new Size(0d, SizeUnit.mm));
    }
    else if (c instanceof TransistorTO126) {
      TransistorTO126 t = (TransistorTO126)c;
      if (t.getFolded())
        t.setLeadLength(new Size(0d, SizeUnit.mm));
    }
  }
  
  private void addLeads(PotentiometerPanel c, Project project, Set<IDIYComponent<?>> newSelection) {
    Size offsetY = new Size(0.5d, SizeUnit.in);        
    
    // don't do it for PCB type
    if (c.getType() == Type.PCB)
      return;        
    
    // create leads
    for (int i = 0; i < 3; i++) {
      Point p0 = c.getControlPoint(i);
      double dx = 0;
      double dy = offsetY.convertToPixels();
      
      AffineTransform tx = null;
      if (c.getOrientation() != Orientation.DEFAULT)
        tx = AffineTransform.getRotateInstance(c.getOrientation().toRadians(), p0.x, p0.y);
            
      HookupWire w = new HookupWire();
      getInstantiationManager().fillWithDefaultProperties(w, null);
      for (int j = 0; j < w.getControlPointCount(); j++) {
        Point p = new Point((int)(p0.x + dx * j), (int)(p0.y + dy * j));
          if (tx != null)
          tx.transform(p, p);                  
        w.setControlPoint(p, j);
      }
 
      newSelection.add(w);    
      
      // inject leads right before the component
      int index = project.getComponents().indexOf(c);
      project.getComponents().add(index + 1, w);
    }
  }
  
  private void addLeads(TubeSocket c, Project project, Set<IDIYComponent<?>> newSelection) {
    Point center = c.getControlPoint(0);
    
    // don't do it for PCB type
    if (c.getMount() == Mount.PCB)
      return;    
    
    // create leads
    for (int i = 1; i < c.getControlPointCount(); i++) {
      Point p = c.getControlPoint(i);
      double dx = p.x - center.x;
      double dy = p.y - center.y;
      
      // calculate lead position    
      HookupWire w = new HookupWire();
      getInstantiationManager().fillWithDefaultProperties(w, null);
      for (int j = 0; j < w.getControlPointCount(); j++) {
        Point pw = new Point((int)(p.x + dx * j), (int)(p.y + dy * j));
        w.setControlPoint(pw, j);
      }  
      newSelection.add(w);    
      
      // inject leads right before the component
      int index = project.getComponents().indexOf(c);
      project.getComponents().add(index + 1, w);
    }
  }
  
  private void addLeads(AbstractGuitarPickup c, Project project, Set<IDIYComponent<?>> newSelection) {
    Size offset = new Size(1d, SizeUnit.in);        
    
    // create leads
    for (int i = 0; i < c.getControlPointCount(); i++) {
      if (!c.isControlPointSticky(i))
        continue;
      Point p0 = c.getControlPoint(i);
      double dx = 0;
      double dy = 0;
      
      if (c instanceof SingleCoilPickup)
        dy = offset.convertToPixels();
      else
        dx = offset.convertToPixels();      
      
      AffineTransform tx = null;
      if (c.getOrientation() != Orientation.DEFAULT)
        tx = AffineTransform.getRotateInstance(c.getOrientation().toRadians(), p0.x, p0.y);
            
      HookupWire w = new HookupWire();
      w.setGauge(AWG._24);
      getInstantiationManager().fillWithDefaultProperties(w, null);
      for (int j = 0; j < w.getControlPointCount(); j++) {
        Point p = new Point((int)(p0.x + dx * j), (int)(p0.y + dy * j));
          if (tx != null)
          tx.transform(p, p);                  
        w.setControlPoint(p, j);
      }
 
      newSelection.add(w);    
      
      // inject leads right before the component
      int index = project.getComponents().indexOf(c);
      project.getComponents().add(index + 1, w);
    }
  }

  @Override
  public String getEditAction() {
    return "Add Flexible Leads";
  }

  public InstantiationManager getInstantiationManager() {
    if (instantiationManager == null)
      instantiationManager = new InstantiationManager();
    return instantiationManager;
  }
}
