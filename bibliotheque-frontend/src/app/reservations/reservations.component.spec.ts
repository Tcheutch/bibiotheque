import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { ReservationsComponent } from './reservations.component';

describe('ReservationsComponent', () => {
  let component: ReservationsComponent;
  let fixture: ComponentFixture<ReservationsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      declarations: [ ReservationsComponent ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ReservationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('résout les libellés livre/adhérent sans toucher au DTO backend', () => {
    component.livres = [{ bookId: 1, bookName: 'Le Petit Prince', bookAuthor: '', bookGenre: '', noOfCopies: 0 }];
    component.adherents = [{ userId: 2, username: 'a2', name: 'A2 - Test quota', password: '', role: [] }];
    component.reservations = [{
      id: 10, livreId: 1, adherentId: 2,
      dateReservation: new Date(), dateExpiration: new Date(),
      statut: 'EN_ATTENTE' as any
    }];

    const [affichage] = component.reservationsAffichables;

    expect(affichage.titreLivre).toBe('Le Petit Prince');
    expect(affichage.nomAdherent).toBe('A2 - Test quota');
    expect((affichage as any).id).toBe(10);
  });
});
