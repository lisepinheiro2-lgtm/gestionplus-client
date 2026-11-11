package gestionTest.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import gestion.model.Client;
import gestion.model.Employee;
import gestion.model.Intervention;
import gestion.model.RecurringIntervention;
import gestion.repository.AbsenceCsvRepository;
import gestion.repository.ClientCsvRepository;
import gestion.repository.EmployeeCsvRepository;
import gestion.repository.InterventionCsvRepository;
import gestion.repository.RecurringInterventionCsvRepository;
import gestion.repository.ReplacementCsvRepository;
import gestion.service.AbsenceService;
import gestion.service.ClientService;
import gestion.service.PersonnelService;
import gestion.service.PlanningService;
import gestion.service.RecurringInterventionService;
import gestion.service.ReplacementService;

public final class TestFixture {

	public final Path directory;
	public final PersonnelService personnel;
	public final ClientService clients;
	public final PlanningService planning;
	public final RecurringInterventionService recurring;
	public final ReplacementService replacements;
	public final AbsenceService absences;
	public final Employee a, b, c;
	public final Client client;
	public final LocalDate future = LocalDate.now().plusYears(1)
			.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

	public TestFixture(Path directory) throws IOException {
		this.directory = directory;
		personnel = new PersonnelService(new EmployeeCsvRepository(directory));
		clients = new ClientService(new ClientCsvRepository(directory));
		planning = new PlanningService(personnel, clients, new InterventionCsvRepository(directory));
		recurring = new RecurringInterventionService(clients, personnel,
				new RecurringInterventionCsvRepository(directory));
		replacements = new ReplacementService(new ReplacementCsvRepository(directory));
		absences = new AbsenceService(personnel, replacements, planning, new AbsenceCsvRepository(directory));
		personnel.addEmployee("Ana", "Alpha", 35, "Opérationnel", 0, 0);
		personnel.addEmployee("Béa", "Beta", 35, "Opérationnel", 0, 0);
		personnel.addEmployee("Célia", "Gamma", 35, "Opérationnel", 0, 0);
		a = personnel.findEmployee("Ana", "Alpha");
		b = personnel.findEmployee("Béa", "Beta");
		c = personnel.findEmployee("Célia", "Gamma");
		clients.addClient("Client", "Fictif", "Adresse fictive", 0, null, null);
		client = clients.getClients().getFirst();
	}

	public Intervention add(Employee employee, LocalDate day, int start, int end) {
		Intervention intervention = new Intervention(client, employee, day, LocalTime.of(start, 0), day,
				LocalTime.of(end, 0));
		planning.addIntervention(intervention);

		return intervention;
	}

	public RecurringIntervention series(Employee employee, LocalDate start, LocalDate end) {
		return new RecurringIntervention(client, employee, start.getDayOfWeek(), LocalTime.of(8, 0),
				LocalTime.of(10, 0), start, end);
	}

	public Map<String, String> files() throws IOException {
		Map<String, String> state = new TreeMap<>();

		try (var paths = Files.list(directory)) {

			for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".csv") && Files.isRegularFile(p))
					.toList()) {
				state.put(path.getFileName().toString(), Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
			}

		}

		return state;
	}

	public void reload() throws IOException {
		new gestion.app.ApplicationContext(directory);
	}
}
