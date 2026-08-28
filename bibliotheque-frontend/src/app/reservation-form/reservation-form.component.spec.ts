import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';

import { ReservationFormComponent } from './reservation-form.component';

describe('ReservationFormComponent', () => {
  let component: ReservationFormComponent;
  let fixture: ComponentFixture<ReservationFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [ ReservationFormComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ReservationFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it("n'émet rien tant que les deux champs ne sont pas renseignés", () => {
    spyOn(component.creer, 'emit');

    component.livreId = undefined;
    component.adherentId = 2;
    component.onSubmit();

    expect(component.creer.emit).not.toHaveBeenCalled();
  });

  it('émet uniquement livreId et adherentId une fois les deux champs renseignés', () => {
    spyOn(component.creer, 'emit');

    component.livreId = 1;
    component.adherentId = 2;
    component.onSubmit();

    expect(component.creer.emit).toHaveBeenCalledWith({ livreId: 1, adherentId: 2 });
  });
});
