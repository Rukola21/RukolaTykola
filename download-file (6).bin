package com.notevault.ui.notes

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.notevault.R
import com.notevault.data.local.entities.Note
import com.notevault.databinding.FragmentNoteListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteListFragment : Fragment() {

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteListViewModel by viewModels()
    private lateinit var adapter: NoteAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = NoteAdapter(
            onNoteClick = { note -> openNote(note) },
            onNoteLongClick = { note -> showNoteContextMenu(note) }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)
    }

    private fun applyLayoutManager(mode: String) {
        binding.recyclerView.layoutManager = if (mode == "list") {
            LinearLayoutManager(requireContext())
        } else {
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        }
        adapter.setViewMode(mode)
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupFab() {
        binding.fabNewNote.setOnClickListener {
            findNavController().navigate(
                NoteListFragmentDirections.actionListToEditor()
            )
        }


    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.notes.collect { notes ->
                        adapter.submitList(notes)
                        binding.emptyState.isVisible = notes.isEmpty()
                        binding.recyclerView.isVisible = notes.isNotEmpty()
                        binding.tvNotesCount.text = when (notes.size) {
                            0 -> ""
                            1 -> "1 заметка"
                            in 2..4 -> "${notes.size} заметки"
                            else -> "${notes.size} заметок"
                        }
                    }
                }

                launch {
                    viewModel.notesViewMode.collect { mode ->
                        applyLayoutManager(mode)
                        binding.btnToggleView.setImageResource(
                            if (mode == "grid") R.drawable.ic_list else R.drawable.ic_grid
                        )
                    }
                }

                launch {
                    viewModel.allTags.collect { tags ->
                        populateTagChips(tags)
                    }
                }
            }
        }

        binding.btnToggleView.setOnClickListener {
            val current = if (adapter.currentViewMode == "grid") "list" else "grid"
            viewModel.setViewMode(current)
        }
    }

    private fun populateTagChips(tags: List<String>) {
        binding.chipGroupTags.removeAllViews()

        // "Все" чип
        val allChip = Chip(requireContext()).apply {
            text = "Все"
            isCheckable = true
            isChecked = viewModel.selectedTag.value == null
            setOnCheckedChangeListener { _, checked ->
                if (checked) viewModel.filterByTag(null)
            }
        }
        binding.chipGroupTags.addView(allChip)

        tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = "#$tag"
                isCheckable = true
                isChecked = viewModel.selectedTag.value == tag
                setOnCheckedChangeListener { _, checked ->
                    if (checked) viewModel.filterByTag(tag)
                }
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun openNote(note: Note) {
        findNavController().navigate(
            NoteListFragmentDirections.actionListToEditor(note.id)
        )
    }

    private fun showNoteContextMenu(note: Note) {
        val items = arrayOf(
            if (note.isPinned) "📌 Открепить" else "📌 Закрепить",
            "🗑 Удалить"
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(note.title.ifBlank { note.content.take(40).ifBlank { "Заметка" } })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.togglePin(note)
                        val msg = if (note.isPinned) "Откреплено" else "Закреплено"
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                    1 -> confirmDelete(note)
                }
            }
            .show()
    }

    private fun confirmDelete(note: Note) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Удалить заметку?")
            .setMessage("Все вложения также будут удалены.")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteNote(note)
                Snackbar.make(binding.root, "Заметка удалена", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
